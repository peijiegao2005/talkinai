package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.entity.ChatMessage;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/messages/private/{userId}")
    public Mono<ApiResponse<List<ChatMessage>>> getPrivateMessages(
            Authentication authentication,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String currentUserId = authentication.getPrincipal().toString();
        log.info("获取私聊消息: currentUser={}, targetUser={}", currentUserId, userId);

        return chatService.getPrivateMessages(currentUserId, userId, page, size)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/messages/room/{roomId}")
    public Mono<ApiResponse<List<ChatMessage>>> getRoomMessages(
            Authentication authentication,
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String userId = authentication.getPrincipal().toString();
        log.info("获取群聊消息: userId={}, roomId={}", userId, roomId);

        return chatService.getRoomMessages(roomId, page, size)
                .collectList()
                .map(ApiResponse::success);
    }

    @GetMapping("/rooms")
    public Mono<ApiResponse<List<ChatRoom>>> getMyRooms(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("获取用户聊天室列表: userId={}", userId);

        return chatService.getUserRooms(userId)
                .collectList()
                .map(ApiResponse::success);
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
