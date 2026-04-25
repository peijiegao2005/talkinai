package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.dto.MatchResponse;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.service.ChatService;
import com.talkingai.soulchat.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final ChatService chatService;

    @PostMapping("/find-soul")
    public Mono<ApiResponse<MatchResponse>> findSoulMatch(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户寻找灵魂伴侣: userId={}", userId);

        return matchingService.findSoulMatch(userId)
                .flatMap(matchUser ->
                    chatService.getOrCreateMatchRoom(userId, matchUser.getId())
                            .map(room -> buildMatchResponse(matchUser, room))
                )
                .map(ApiResponse::success)
                .onErrorResume(e -> {
                    log.warn("匹配失败: userId={}, error={}", userId, e.getMessage());
                    return Mono.just(ApiResponse.error(400, e.getMessage()));
                });
    }

    @GetMapping("/status")
    public Mono<ApiResponse<Boolean>> getOnlineStatus(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        return matchingService.isUserOnline(userId)
                .map(ApiResponse::success);
    }

    @PostMapping("/online")
    public Mono<ApiResponse<Void>> markOnline(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户上线: userId={}", userId);

        return matchingService.markUserOnline(userId)
                .thenReturn(ApiResponse.success(null));
    }

    @PostMapping("/offline")
    public Mono<ApiResponse<Void>> markOffline(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户下线: userId={}", userId);

        return matchingService.markUserOffline(userId)
                .thenReturn(ApiResponse.success(null));
    }

    private MatchResponse buildMatchResponse(User matchUser, ChatRoom room) {
        return MatchResponse.builder()
                .matched(true)
                .matchUserId(matchUser.getId())
                .matchNickname(matchUser.getNickname())
                .matchAvatar(matchUser.getAvatar())
                .roomId(room.getRoomId())
                .personalityVector(matchUser.getPersonalityVector())
                .build();
    }
}
