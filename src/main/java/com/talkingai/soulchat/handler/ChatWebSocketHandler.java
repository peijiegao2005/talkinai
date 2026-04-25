package com.talkingai.soulchat.handler;

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

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final MatchingService matchingService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // 用户ID -> WebSocketSession
    private static final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    // 用户ID -> Sink (用于发送消息给特定用户)
    private static final ConcurrentHashMap<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            return session.close();
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        log.info("WebSocket连接: userId={}, sessionId={}", userId, session.getId());

        // 标记用户在线
        matchingService.markUserOnline(userId).subscribe();

        // 为该用户创建消息Sink
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        userSessions.put(userId, session);
        userSinks.put(userId, sink);

        // 处理接收到的消息
        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(message -> handleMessage(userId, message))
                .then();

        // 发送消息给客户端
        Mono<Void> output = session.send(
                sink.asFlux()
                        .map(session::textMessage)
        );

        // 连接断开时清理
        Mono<Void> cleanup = Mono.fromRunnable(() -> {
            log.info("WebSocket断开: userId={}", userId);
            userSessions.remove(userId);
            userSinks.remove(userId);
            matchingService.markUserOffline(userId).subscribe();
        });

        return Mono.zip(input, output)
                .then(cleanup)
                .onErrorResume(e -> {
                    log.error("WebSocket错误: userId={}, error={}", userId, e.getMessage());
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
            log.error("解析消息失败: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> handleChatMessage(String senderId, WebSocketMessage message) {
        String receiverId = message.getReceiverId();
        String content = message.getContent();

        return chatService.saveMessage(senderId, receiverId, content)
                .flatMap(savedMessage -> {
                    // 发送给接收者
                    sendMessageToUser(receiverId, savedMessage);
                    // 发送确认给发送者
                    sendMessageToUser(senderId, savedMessage);
                    return Mono.empty();
                });
    }

    private Mono<Void> handleJoinRoom(String userId, WebSocketMessage message) {
        String roomId = message.getRoomId();
        return chatService.joinRoom(userId, roomId)
                .doOnSuccess(v -> {
                    WebSocketMessage systemMsg = new WebSocketMessage();
                    systemMsg.setType("SYSTEM");
                    systemMsg.setContent("已加入聊天室");
                    sendMessageToUser(userId, systemMsg);
                })
                .then();
    }

    private Mono<Void> handleLeaveRoom(String userId, WebSocketMessage message) {
        String roomId = message.getRoomId();
        return chatService.leaveRoom(userId, roomId).then();
    }

    private Mono<Void> handleTyping(String userId, WebSocketMessage message) {
        String receiverId = message.getReceiverId();
        WebSocketMessage typingMsg = new WebSocketMessage();
        typingMsg.setType("TYPING");
        typingMsg.setSenderId(userId);
        sendMessageToUser(receiverId, typingMsg);
        return Mono.empty();
    }

    public void sendMessageToUser(String userId, Object message) {
        Sinks.Many<String> sink = userSinks.get(userId);
        if (sink != null) {
            try {
                String json = objectMapper.writeValueAsString(message);
                sink.tryEmitNext(json);
            } catch (Exception e) {
                log.error("发送消息失败: userId={}, error={}", userId, e.getMessage());
            }
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
    }
}
