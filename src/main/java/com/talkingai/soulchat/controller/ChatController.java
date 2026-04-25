package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.entity.ChatMessage;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/messages/private/{userId}")
    public Mono<ApiResponse<Flux<ChatMessage>>> getPrivateMessages(
            Authentication authentication,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String currentUserId = authentication.getPrincipal().toString();
        log.info("获取私聊消息: currentUser={}, targetUser={}", currentUserId, userId);

        Flux<ChatMessage> messages = chatService.getPrivateMessages(currentUserId, userId, page, size);
        return Mono.just(ApiResponse.success(messages));
    }

    @GetMapping("/messages/room/{roomId}")
    public Mono<ApiResponse<Flux<ChatMessage>>> getRoomMessages(
            Authentication authentication,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = authentication.getPrincipal().toString();
        log.info("获取群聊消息: userId={}, roomId={}", userId, roomId);

        Flux<ChatMessage> messages = chatService.getRoomMessages(roomId, page, size);
        return Mono.just(ApiResponse.success(messages));
    }

    @GetMapping("/rooms")
    public Mono<ApiResponse<Flux<ChatRoom>>> getMyRooms(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("获取用户聊天室列表: userId={}", userId);

        Flux<ChatRoom> rooms = chatService.getUserRooms(userId);
        return Mono.just(ApiResponse.success(rooms));
    }

    @GetMapping("/unread-count")
    public Mono<ApiResponse<Map<String, Object>>> getUnreadCount(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();

        return chatService.getUnreadCount(userId)
                .map(count -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("unreadCount", count);
                    return ApiResponse.success(result);
                });
    }

    @PostMapping("/messages/{messageId}/read")
    public Mono<ApiResponse<Void>> markAsRead(
            Authentication authentication,
            @PathVariable String messageId) {
        String userId = authentication.getPrincipal().toString();
        log.info("标记消息已读: userId={}, messageId={}", userId, messageId);

        return chatService.markAsRead(messageId)
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/messages/read-all/{senderId}")
    public Mono<ApiResponse<Void>> markAllAsRead(
            Authentication authentication,
            @PathVariable String senderId) {
        String userId = authentication.getPrincipal().toString();
        log.info("标记所有消息已读: userId={}, senderId={}", userId, senderId);

        return chatService.markAllAsRead(userId, senderId)
                .thenReturn(ApiResponse.success(null));
    }
}
