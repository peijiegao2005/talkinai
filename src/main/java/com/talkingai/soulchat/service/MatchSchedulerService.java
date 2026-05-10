package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.entity.UserProfile;
import com.talkingai.soulchat.repository.UserProfileRepository;
import com.talkingai.soulchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量匹配调度器
 * 定期扫描匹配队列，批量计算匹配分数，降低实时匹配压力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchSchedulerService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    private static final String MATCHING_QUEUE_KEY = "matching:queue";
    private static final String PRE_COMPUTED_KEY = "matching:precomputed";

    // 人格维度权重
    private static final double[] DIMENSION_WEIGHTS = {
            0.25, 0.20, 0.25, 0.15, 0.15
    };

    @Value("${matching.batch-size:20}")
    private int batchSize;

    @Value("${matching.max-candidates:100}")
    private int maxCandidates;

    private final AtomicInteger scheduledRounds = new AtomicInteger(0);

    /**
     * 每5秒执行一次批量匹配预计算
     */
    @Scheduled(fixedDelayString = "${matching.schedule-interval:5000}")
    public void batchPreComputeMatches() {
        int round = scheduledRounds.incrementAndGet();

        redisTemplate.opsForSet()
                .size(MATCHING_QUEUE_KEY)
                .flatMapMany(queueSize -> {
                    if (queueSize < 2) {
                        log.debug("Batch match round {}: queue too small ({})", round, queueSize);
                        return Flux.empty();
                    }

                    log.info("Batch match round {}: queue size = {} (batch: {}, max candidates: {})",
                            round, queueSize, batchSize, maxCandidates);

                    // 从队列取出用户
                    return redisTemplate.opsForSet()
                            .pop(MATCHING_QUEUE_KEY, Math.min(batchSize, queueSize.intValue()))
                            .flatMap(userId -> userRepository.findById(userId))
                            .filter(user -> user.getPersonalityVector() != null)
                            .collectList()
                            .flatMapMany(users -> {
                                if (users.size() < 2) {
                                    // 放回队列
                                    return Flux.fromIterable(users)
                                            .flatMap(u -> redisTemplate.opsForSet()
                                                    .add(MATCHING_QUEUE_KEY, u.getId()));
                                }
                                return batchMatch(users);
                            });
                })
                .subscribe(
                        result -> {},
                        error -> log.error("Batch match round {} error: {}", round, error.getMessage()),
                        () -> log.debug("Batch match round {} completed", round)
                );
    }

    /**
     * 批量匹配计算
     */
    private Flux<String> batchMatch(List<User> users) {
        int total = users.size();
        // 限制候选数，防止O(n²)爆炸
        int capped = Math.min(total, maxCandidates);

        List<User> candidates = total > maxCandidates
                ? users.subList(0, capped)
                : users;

        // 计算所有用户的两两匹配分数
        List<Mono<Void>> matchTasks = new ArrayList<>();
        Set<String> matched = new HashSet<>();

        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                User userA = candidates.get(i);
                User userB = candidates.get(j);

                if (matched.contains(userA.getId()) || matched.contains(userB.getId())) {
                    continue;
                }

                matchTasks.add(
                        computePairScore(userA, userB)
                                .flatMap(score -> {
                                    if (score >= 0.40) {
                                        matched.add(userA.getId());
                                        matched.add(userB.getId());
                                        return storePreComputedMatch(userA.getId(), userB.getId(), score);
                                    }
                                    return Mono.empty();
                                })
                                .onErrorResume(e -> Mono.empty())
                );

                if (matchTasks.size() >= batchSize) break;
            }
            if (matchTasks.size() >= batchSize) break;
        }

        // 未匹配的用户放回队列
        List<String> unmatched = candidates.stream()
                .map(User::getId)
                .filter(id -> !matched.contains(id))
                .toList();

        if (!unmatched.isEmpty()) {
            matchTasks.add(
                    Flux.fromIterable(unmatched)
                            .flatMap(id -> redisTemplate.opsForSet().add(MATCHING_QUEUE_KEY, id))
                            .then()
            );
        }

        return Flux.concat(matchTasks)
                .thenMany(Flux.fromIterable(unmatched));
    }

    /**
     * 计算两个人的匹配分数
     */
    private Mono<Double> computePairScore(User userA, User userB) {
        return Mono.zip(
                userProfileRepository.findByUserId(userA.getId())
                        .defaultIfEmpty(UserProfile.builder().interests(new ArrayList<>()).build()),
                userProfileRepository.findByUserId(userB.getId())
                        .defaultIfEmpty(UserProfile.builder().interests(new ArrayList<>()).build())
        ).map(tuple -> {
            double personalityScore = calculatePersonalitySimilarity(
                    userA.getPersonalityVector(), userB.getPersonalityVector());
            double interestScore = calculateInterestCompatibility(
                    tuple.getT1().getInterests(), tuple.getT2().getInterests());
            return personalityScore * 0.50 + interestScore * 0.30 + 0.20;
        });
    }

    private double calculatePersonalitySimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0.0;
        double dot = 0.0, n1 = 0.0, n2 = 0.0;
        for (int i = 0; i < v1.size() && i < DIMENSION_WEIGHTS.length; i++) {
            double w = DIMENSION_WEIGHTS[i];
            dot += w * v1.get(i) * v2.get(i);
            n1 += w * v1.get(i) * v1.get(i);
            n2 += w * v2.get(i) * v2.get(i);
        }
        if (n1 == 0 || n2 == 0) return 0.0;
        return (dot / (Math.sqrt(n1) * Math.sqrt(n2)) + 1) / 2;
    }

    private double calculateInterestCompatibility(List<String> i1, List<String> i2) {
        if (i1 == null || i2 == null || i1.isEmpty() || i2.isEmpty()) return 0.5;
        Set<String> s1 = new HashSet<>(i1);
        Set<String> s2 = new HashSet<>(i2);
        Set<String> common = new HashSet<>(s1);
        common.retainAll(s2);
        if (common.isEmpty()) return 0.3;
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        double jaccard = (double) common.size() / union.size();
        return Math.min(jaccard + Math.min(common.size() * 0.1, 0.3), 1.0);
    }

    /**
     * 存储预计算匹配结果到Redis
     */
    private Mono<Void> storePreComputedMatch(String userId1, String userId2, double score) {
        String scoreKey = String.format("%s:score:%s:%s", PRE_COMPUTED_KEY, userId1, userId2);
        return redisTemplate.opsForValue()
                .set(scoreKey, String.valueOf(score), Duration.ofSeconds(30))
                .doOnSuccess(v -> log.debug("Pre-computed match: {} <-> {} = {:.2f}",
                        userId1, userId2, score))
                .then();
    }
}
