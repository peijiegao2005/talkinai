package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final UserRepository userRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String ONLINE_KEY_PREFIX = "user:online:";
    private static final String MATCHING_QUEUE_KEY = "matching:queue";
    private static final Duration ONLINE_TTL = Duration.ofMinutes(5);

    public Mono<User> findSoulMatch(String userId) {
        return userRepository.findById(userId)
                .flatMap(currentUser -> {
                    if (currentUser.getPersonalityVector() == null) {
                        return Mono.error(new RuntimeException("请先完成性格评估"));
                    }
                    return findOnlineMatch(userId, currentUser.getPersonalityVector());
                });
    }

    private Mono<User> findOnlineMatch(String userId, List<Double> personalityVector) {
        // 先尝试从匹配队列找
        return redisTemplate.opsForSet()
                .members(MATCHING_QUEUE_KEY)
                .filter(id -> !id.equals(userId))
                .next()
                .flatMap(candidateId ->
                    redisTemplate.opsForSet()
                            .remove(MATCHING_QUEUE_KEY, candidateId)
                            .then(userRepository.findById(candidateId))
                )
                .switchIfEmpty(Mono.defer(() -> {
                    // 队列为空，将自己加入队列等待
                    return redisTemplate.opsForSet()
                            .add(MATCHING_QUEUE_KEY, userId)
                            .then(Mono.error(new RuntimeException("正在寻找灵魂伴侣，请稍后再试")));
                }))
                .flatMap(matchUser -> {
                    // 创建匹配关系，双方从队列移除
                    return redisTemplate.opsForSet()
                            .remove(MATCHING_QUEUE_KEY, userId)
                            .thenReturn(matchUser);
                })
                .doOnSuccess(match -> log.info("用户 {} 匹配到 {}", userId, match.getId()))
                .doOnError(e -> log.warn("用户 {} 匹配失败: {}", userId, e.getMessage()));
    }

    public Mono<Void> markUserOnline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate.opsForValue()
                .set(key, "1", ONLINE_TTL)
                .then();
    }

    public Mono<Void> markUserOffline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate.delete(key)
                .then(redisTemplate.opsForSet().remove(MATCHING_QUEUE_KEY, userId))
                .then();
    }

    public Mono<Boolean> isUserOnline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate.hasKey(key);
    }

    private double calculateSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            norm1 += v1.get(i) * v1.get(i);
            norm2 += v2.get(i) * v2.get(i);
        }
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
