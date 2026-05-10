package com.talkingai.soulchat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis滑动窗口限流服务
 * 4核4G适配：单用户级 + 全局限流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    @Value("${rate-limit.match.permits-per-second:5}")
    private int matchPermitsPerSecond;

    @Value("${rate-limit.match.burst-size:10}")
    private int matchBurstSize;

    @Value("${rate-limit.mood-assessment.permits-per-second:3}")
    private int moodPermitsPerSecond;

    @Value("${rate-limit.mood-assessment.burst-size:5}")
    private int moodBurstSize;

    /**
     * 检查匹配接口限流（用户级别）
     */
    public Mono<Boolean> tryAcquireMatch(String userId) {
        return tryAcquire("match:user:" + userId, matchPermitsPerSecond, matchBurstSize);
    }

    /**
     * 检查匹配接口限流（全局级别）
     */
    public Mono<Boolean> tryAcquireMatchGlobal() {
        return tryAcquire("match:global", matchPermitsPerSecond * 4, matchBurstSize * 2);
    }

    /**
     * 检查心情评测接口限流（用户级别）
     */
    public Mono<Boolean> tryAcquireMoodAssessment(String userId) {
        return tryAcquire("mood:user:" + userId, moodPermitsPerSecond, moodBurstSize);
    }

    /**
     * 滑动窗口限流实现
     * 使用 Redis INCR + EXPIRE 实现固定窗口计数
     * 简单高效，适合4核4G场景
     */
    private Mono<Boolean> tryAcquire(String key, int permitsPerSecond, int burstSize) {
        String windowKey = RATE_LIMIT_PREFIX + key + ":" + (System.currentTimeMillis() / 1000);

        return redisTemplate.opsForValue()
                .increment(windowKey)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(windowKey, Duration.ofSeconds(2))
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .map(count -> count <= burstSize)
                .onErrorResume(e -> {
                    log.warn("Rate limit check failed for {}, allowing: {}", key, e.getMessage());
                    return Mono.just(true);
                });
    }
}
