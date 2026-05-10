package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.dto.MatchResponse;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.handler.ChatWebSocketHandler;
import com.talkingai.soulchat.service.ChatService;
import com.talkingai.soulchat.service.MatchingService;
import com.talkingai.soulchat.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;
    private final ChatService chatService;
    private final RateLimiterService rateLimiterService;
    private final ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 寻找灵魂匹配
     * 双重限流：用户级 + 全局级
     * 没匹配到人时：进入匹配池等待，不返回错误
     */
    @PostMapping("/find-soul")
    public Mono<ApiResponse<MatchResponse>> findSoulMatch(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        log.info("用户寻找灵魂伴侣: userId={}", userId);

        return rateLimiterService.tryAcquireMatchGlobal()
                .flatMap(globalAllowed -> {
                    if (!globalAllowed) {
                        log.warn("匹配接口全局限流触发: userId={}", userId);
                        return Mono.just(ApiResponse.error(429, "匹配服务繁忙，请15秒后再试"));
                    }
                    return rateLimiterService.tryAcquireMatch(userId)
                            .flatMap(userAllowed -> {
                                if (!userAllowed) {
                                    log.warn("匹配接口用户限流触发: userId={}", userId);
                                    return Mono.just(ApiResponse.error(429, "操作太频繁，请30秒后再试"));
                                }
                                return doMatch(userId);
                            });
                });
    }

    private Mono<ApiResponse<MatchResponse>> doMatch(String userId) {
        return matchingService.findSoulMatch(userId)
                .flatMap(matchResult -> {
                    if (!matchResult.isMatched()) {
                        // 进入匹配池等待中
                        MatchResponse response = MatchResponse.builder()
                                .matched(false)
                                .waiting(true)
                                .queuePosition(matchResult.getQueuePosition())
                                .matchReason(matchResult.getMatchReason())
                                .build();
                        return Mono.just(ApiResponse.success(response));
                    }
                    // 匹配成功，创建聊天室，并通知被匹配的用户
                    String matchedUserId = matchResult.getMatchedUser().getId();
                    return chatService.getOrCreateMatchRoom(userId, matchedUserId)
                            .doOnSuccess(room -> {
                                User matchedUser = matchResult.getMatchedUser();
                                chatWebSocketHandler.sendMatchNotification(
                                        matchedUserId,
                                        userId,       // partnerId = sender
                                        "",            // frontend will fetch from room API
                                        "",
                                        room.getRoomId()
                                );
                            })
                            .map(room -> buildMatchResponse(matchResult, room))
                            .map(ApiResponse::success);
                })
                .onErrorResume(e -> {
                    log.warn("匹配失败: userId={}, error={}", userId, e.getMessage());
                    return Mono.just(ApiResponse.error(400, e.getMessage()));
                });
    }

    /**
     * 查询匹配队列状态
     */
    @GetMapping("/queue-status")
    public Mono<ApiResponse<Map<String, Object>>> getQueueStatus(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        return matchingService.getQueueStatus(userId)
                .map(ApiResponse::success);
    }

    /**
     * 离开匹配队列
     */
    @PostMapping("/leave-queue")
    public Mono<ApiResponse<Void>> leaveQueue(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        return matchingService.leaveQueue(userId)
                .thenReturn(ApiResponse.success(null));
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

    private MatchResponse buildMatchResponse(MatchingService.MatchResult matchResult, ChatRoom room) {
        User matchUser = matchResult.getMatchedUser();
        return MatchResponse.builder()
                .matched(true)
                .waiting(false)
                .matchUserId(matchUser.getId())
                .matchNickname(matchUser.getNickname())
                .matchAvatar(matchUser.getAvatar())
                .roomId(room.getRoomId())
                .personalityVector(matchUser.getPersonalityVector())
                .matchScore(matchResult.getMatchScore())
                .matchLevel(matchResult.getMatchLevelLabel())
                .matchLevelDescription(matchResult.getMatchLevelDescription())
                .personalityCompatibility(matchResult.getPersonalityCompatibility())
                .interestCompatibility(matchResult.getInterestCompatibility())
                .commonInterests(matchResult.getCommonInterests())
                .matchReason(matchResult.getMatchReason())
                .build();
    }
}
