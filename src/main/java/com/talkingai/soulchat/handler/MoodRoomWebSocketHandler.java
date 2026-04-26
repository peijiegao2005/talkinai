package com.talkingai.soulchat.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkingai.soulchat.entity.MoodChatMessage;
import com.talkingai.soulchat.service.MoodRoomService;
import com.talkingai.soulchat.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MoodRoomWebSocketHandler implements WebSocketHandler {

    private final MoodRoomService moodRoomService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // 存储用户会话
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    public MoodRoomWebSocketHandler(MoodRoomService moodRoomService, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.moodRoomService = moodRoomService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 从URL参数获取token和roomId
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractParam(query, "token");
        String roomId = extractParam(query, "roomId");

        if (token == null || roomId == null) {
            return session.close();
        }

        // 验证token
        String userId;
        try {
            if (!jwtUtil.validateToken(token)) {
                return session.close();
            }
            userId = jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("WebSocket token validation failed", e);
            return session.close();
        }

        // 存储会话
        userSessions.put(userId, session);
        sessionToUser.put(session.getId(), userId);

        log.info("用户 {} 连接到心情聊天室 WebSocket, room: {}", userId, roomId);

        // 订阅房间消息
        Flux<String> roomMessages = moodRoomService.subscribeToRoom(roomId)
                .map(this::toJson);

        // 处理收到的消息
        Mono<Void> input = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .flatMap(payload -> handleMessage(userId, roomId, payload))
                .onErrorContinue((err, obj) -> log.error("Error handling message", err))
                .then();

        // 发送消息到客户端
        Mono<Void> output = session.send(
                roomMessages.map(session::textMessage)
        );

        // 清理会话
        Mono<Void> cleanup = Mono.fromRunnable(() -> {
            userSessions.remove(userId);
            sessionToUser.remove(session.getId());
            log.info("用户 {} 断开心情聊天室 WebSocket", userId);
        });

        return Mono.zip(input, output)
                .then(cleanup)
                .onErrorResume(e -> {
                    log.error("WebSocket error for user {}: {}", userId, e.getMessage());
                    userSessions.remove(userId);
                    sessionToUser.remove(session.getId());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleMessage(String userId, String roomId, String payload) {
        try {
            WebSocketMessage message = objectMapper.readValue(payload, WebSocketMessage.class);

            switch (message.getType()) {
                case "CHAT":
                    return moodRoomService.sendMessage(userId, roomId, message.getContent())
                            .then();
                case "PING":
                    // 心跳，不需要处理
                    return Mono.empty();
                default:
                    return Mono.empty();
            }
        } catch (Exception e) {
            log.error("Failed to parse WebSocket message", e);
            return Mono.empty();
        }
    }

    private String extractParam(String query, String paramName) {
        if (query == null) return null;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize message", e);
            return "";
        }
    }

    // WebSocket消息格式
    private static class WebSocketMessage {
        private String type;
        private String content;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
