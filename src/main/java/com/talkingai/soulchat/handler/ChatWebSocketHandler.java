package com.talkingai.soulchat.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkingai.soulchat.entity.ChatMessage;
import com.talkingai.soulchat.service.ChatService;
import com.talkingai.soulchat.service.MatchingService;
import com.talkingai.soulchat.util.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final MatchingService matchingService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private static final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            return session.close();
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());

        matchingService.markUserOnline(userId).subscribe();

        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        userSessions.put(userId, session);
        userSinks.put(userId, sink);

        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(message -> handleMessage(userId, message))
                .then();

        Mono<Void> output = session.send(
                sink.asFlux().map(session::textMessage)
        );

        Mono<Void> cleanup = Mono.fromRunnable(() -> {
            log.info("WebSocket disconnected: userId={}", userId);
            userSessions.remove(userId);
            Sinks.Many<String> removed = userSinks.remove(userId);
            if (removed != null) {
                removed.tryEmitComplete();
            }
            matchingService.markUserOffline(userId).subscribe();
        });

        return Mono.zip(input, output).then(cleanup).onErrorResume(e -> {
            log.error("WebSocket error: userId={}, error={}", userId, e.getMessage());
            return cleanup;
        });
    }

    private Mono<Void> handleMessage(String userId, String messageJson) {
        try {
            WebSocketMessage message = objectMapper.readValue(messageJson, WebSocketMessage.class);

            return switch (message.getType()) {
                case "CHAT" -> handleChatMessage(userId, message);
                case "JOIN_ROOM" -> handleJoinRoom(userId, message);
                case "LEAVE_ROOM" -> handleLeaveRoom(userId, message);
                case "TYPING" -> handleTyping(userId, message);
                default -> Mono.empty();
            };
        } catch (Exception e) {
            log.error("Failed to parse message: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> handleChatMessage(String senderId, WebSocketMessage message) {
        String receiverId = message.getReceiverId();
        String content = message.getContent();
        String localId = message.getLocalId();
        log.info("Chat message: senderId={}, receiverId={}, content={}, localId={}", senderId, receiverId, content, localId);

        return chatService.saveMessage(senderId, receiverId, content)
                .flatMap(savedMessage -> {
                    Map<String, Object> msgPacket = new HashMap<>();
                    msgPacket.put("type", "CHAT");
                    msgPacket.put("id", savedMessage.getId() != null ? savedMessage.getId() : "");
                    msgPacket.put("senderId", savedMessage.getSenderId());
                    msgPacket.put("receiverId", savedMessage.getReceiverId() != null ? savedMessage.getReceiverId() : "");
                    msgPacket.put("content", savedMessage.getContent());
                    msgPacket.put("timestamp", savedMessage.getTimestamp());
                    if (localId != null && !localId.isEmpty()) {
                        msgPacket.put("_localId", localId);
                    }

                    sendToUser(receiverId, msgPacket);
                    sendToUser(senderId, msgPacket);
                    return Mono.empty();
                });
    }

    private Mono<Void> handleJoinRoom(String userId, WebSocketMessage message) {
        String roomId = message.getRoomId();
        return chatService.joinRoom(userId, roomId)
                .doOnSuccess(v -> sendToUser(userId, Map.of(
                        "type", "SYSTEM",
                        "content", "已加入聊天室"
                )))
                .then();
    }

    private Mono<Void> handleLeaveRoom(String userId, WebSocketMessage message) {
        String roomId = message.getRoomId();
        return chatService.leaveRoom(userId, roomId).then();
    }

    private Mono<Void> handleTyping(String userId, WebSocketMessage message) {
        String receiverId = message.getReceiverId();
        sendToUser(receiverId, Map.of(
                "type", "TYPING",
                "senderId", userId
        ));
        return Mono.empty();
    }

    public void sendMatchNotification(String userId, String partnerId, String partnerNickname,
                                       String partnerAvatar, String roomId) {
        sendToUser(userId, Map.of(
                "type", "MATCHED",
                "matchUserId", partnerId,
                "matchNickname", partnerNickname,
                "matchAvatar", partnerAvatar != null ? partnerAvatar : "👤",
                "roomId", roomId
        ));
    }

    private void sendToUser(String userId, Object message) {
        Sinks.Many<String> sink = userSinks.get(userId);
        if (sink == null) {
            List<String> onlineIds = List.copyOf(userSinks.keySet());
            log.warn("sendToUser FAILED: target userId={} not connected. online users: {}", userId, onlineIds);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(message);
            Sinks.EmitResult result = sink.tryEmitNext(json);
            if (result.isFailure()) {
                log.warn("sendToUser emit FAILED: userId={}, result={}", userId, result);
            }
        } catch (Exception e) {
            log.error("sendToUser error: userId={}, error={}", userId, e.getMessage());
        }
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && query.startsWith("token=")) {
            return query.substring(6);
        }
        return null;
    }

    @Data
    public static class WebSocketMessage {
        private String type;
        private String senderId;
        private String receiverId;
        private String roomId;
        private String content;
        private Long timestamp;
        @JsonProperty("_localId")
        private String localId;
    }
}
