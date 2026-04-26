package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.entity.UserProfile;
import com.talkingai.soulchat.repository.UserProfileRepository;
import com.talkingai.soulchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String ONLINE_KEY_PREFIX = "user:online:";
    private static final String MATCHING_QUEUE_KEY = "matching:queue";
    private static final Duration ONLINE_TTL = Duration.ofMinutes(5);

    // 匹配算法权重配置
    private static final double WEIGHT_PERSONALITY = 0.5;      // 人格相似度权重 50%
    private static final double WEIGHT_INTERESTS = 0.3;        // 共同爱好权重 30%
    private static final double WEIGHT_ONLINE_STATUS = 0.2;    // 在线状态权重 20%

    // 人格维度权重（大五人格）
    private static final double[] DIMENSION_WEIGHTS = {
            0.25,  // extraversion - 外向性（社交匹配重要）
            0.20,  // openness - 开放性（兴趣契合）
            0.25,  // agreeableness - 宜人性（相处和谐）
            0.15,  // conscientiousness - 尽责性（生活方式）
            0.15   // emotional_stability - 情绪稳定性（情绪共鸣）
    };

    // 匹配阈值配置
    private static final double THRESHOLD_HIGH = 0.75;    // 高匹配度阈值
    private static final double THRESHOLD_MEDIUM = 0.55;  // 中等匹配度阈值
    private static final double THRESHOLD_LOW = 0.40;     // 最低可接受阈值

    /**
     * 寻找灵魂匹配 - 多级匹配策略
     * 1. 优先匹配高人格相似度 + 共同爱好的用户
     * 2. 如果没有，降级到仅人格相似度匹配
     * 3. 如果还没有，降级到随机在线用户
     * 4. 最后加入匹配队列等待
     */
    public Mono<MatchResult> findSoulMatch(String userId) {
        return userRepository.findById(userId)
                .flatMap(currentUser -> {
                    if (currentUser.getPersonalityVector() == null) {
                        return Mono.error(new RuntimeException("请先完成性格评估"));
                    }
                    return findBestMatch(userId, currentUser);
                });
    }

    private Mono<MatchResult> findBestMatch(String userId, User currentUser) {
        // 获取当前用户的资料
        return userProfileRepository.findByUserId(userId)
                .defaultIfEmpty(UserProfile.builder().interests(new ArrayList<>()).build())
                .flatMap(currentProfile ->
                    // 获取所有在线用户（包括匹配队列中的用户）
                    getAllAvailableUsers(userId)
                        .collectList()
                        .flatMap(availableUsers -> {
                            if (availableUsers.isEmpty()) {
                                // 没有可用用户，加入队列等待
                                return addToMatchingQueue(userId)
                                        .then(Mono.error(new RuntimeException("正在寻找灵魂伴侣，请稍后再试")));
                            }

                            // 计算每个候选用户的匹配分数
                            return Flux.fromIterable(availableUsers)
                                    .flatMap(candidate -> calculateMatchScore(currentUser, currentProfile, candidate))
                                    .collectList()
                                    .flatMap(scoredCandidates -> {
                                        // 按匹配分数排序
                                        scoredCandidates.sort((a, b) ->
                                                Double.compare(b.getTotalScore(), a.getTotalScore()));

                                        // 降级策略：
                                        // 1. 优先选择达到阈值的匹配
                                        // 2. 如果没有，选择分数最高的（至少有一个候选）
                                        Optional<MatchCandidate> bestMatch = scoredCandidates.stream()
                                                .filter(c -> c.getTotalScore() >= THRESHOLD_LOW)
                                                .findFirst();

                                        if (bestMatch.isPresent()) {
                                            // 找到符合阈值的匹配
                                            MatchCandidate match = bestMatch.get();
                                            return createMatchResult(userId, match);
                                        } else if (!scoredCandidates.isEmpty()) {
                                            // 降级：选择分数最高的候选
                                            MatchCandidate fallbackMatch = scoredCandidates.get(0);
                                            log.info("降级匹配: 用户 {} 匹配到 {}，分数 {}% (低于阈值 {}%)",
                                                    userId, fallbackMatch.getUser().getId(),
                                                    String.format("%.1f", fallbackMatch.getTotalScore() * 100),
                                                    String.format("%.0f", THRESHOLD_LOW * 100));
                                            return createMatchResult(userId, fallbackMatch);
                                        } else {
                                            // 没有可用候选，加入队列等待
                                            return addToMatchingQueue(userId)
                                                    .then(Mono.error(new RuntimeException(
                                                            "暂时没有找到合适的灵魂伴侣，请稍后再试")));
                                        }
                                    });
                        })
                );
    }

    /**
     * 计算匹配分数
     */
    private Mono<MatchCandidate> calculateMatchScore(User currentUser, UserProfile currentProfile, User candidate) {
        return userProfileRepository.findByUserId(candidate.getId())
                .defaultIfEmpty(UserProfile.builder().interests(new ArrayList<>()).build())
                .map(candidateProfile -> {
                    // 1. 计算人格相似度分数 (0-1)
                    double personalityScore = calculatePersonalitySimilarity(
                            currentUser.getPersonalityVector(),
                            candidate.getPersonalityVector()
                    );

                    // 2. 计算共同爱好分数 (0-1)
                    double interestScore = calculateInterestCompatibility(
                            currentProfile.getInterests(),
                            candidateProfile.getInterests()
                    );

                    // 3. 在线状态分数 (固定值，因为都是在线用户)
                    double onlineScore = 1.0;

                    // 4. 计算加权总分
                    double totalScore = personalityScore * WEIGHT_PERSONALITY
                            + interestScore * WEIGHT_INTERESTS
                            + onlineScore * WEIGHT_ONLINE_STATUS;

                    // 5. 确定匹配等级
                    MatchLevel level;
                    if (totalScore >= THRESHOLD_HIGH && personalityScore >= 0.7) {
                        level = MatchLevel.SOULMATE;  // 灵魂伴侣级
                    } else if (totalScore >= THRESHOLD_MEDIUM) {
                        level = MatchLevel.HIGH;      // 高匹配
                    } else if (totalScore >= THRESHOLD_LOW) {
                        level = MatchLevel.MEDIUM;    // 中等匹配
                    } else {
                        level = MatchLevel.LOW;       // 低匹配
                    }

                    return MatchCandidate.builder()
                            .user(candidate)
                            .personalityScore(personalityScore)
                            .interestScore(interestScore)
                            .totalScore(totalScore)
                            .level(level)
                            .commonInterests(getCommonInterests(
                                    currentProfile.getInterests(),
                                    candidateProfile.getInterests()))
                            .build();
                });
    }

    /**
     * 计算人格相似度 - 使用加权余弦相似度
     * 考虑不同维度的重要性
     */
    private double calculatePersonalitySimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) {
            return 0.0;
        }

        double weightedDotProduct = 0.0;
        double weightedNorm1 = 0.0;
        double weightedNorm2 = 0.0;

        for (int i = 0; i < v1.size() && i < DIMENSION_WEIGHTS.length; i++) {
            double weight = DIMENSION_WEIGHTS[i];
            weightedDotProduct += weight * v1.get(i) * v2.get(i);
            weightedNorm1 += weight * v1.get(i) * v1.get(i);
            weightedNorm2 += weight * v2.get(i) * v2.get(i);
        }

        if (weightedNorm1 == 0 || weightedNorm2 == 0) return 0.0;

        // 归一化到 0-1 范围
        double similarity = weightedDotProduct / (Math.sqrt(weightedNorm1) * Math.sqrt(weightedNorm2));
        return (similarity + 1) / 2; // 从 [-1,1] 映射到 [0,1]
    }

    /**
     * 计算爱好兼容性
     * 基于共同爱好数量和总爱好数量
     */
    private double calculateInterestCompatibility(List<String> interests1, List<String> interests2) {
        if (interests1 == null || interests2 == null || interests1.isEmpty() || interests2.isEmpty()) {
            return 0.5; // 默认中等分数
        }

        Set<String> set1 = new HashSet<>(interests1);
        Set<String> set2 = new HashSet<>(interests2);

        // 计算交集
        Set<String> common = new HashSet<>(set1);
        common.retainAll(set2);

        if (common.isEmpty()) {
            return 0.3; // 没有共同爱好，较低分数
        }

        // Jaccard 相似度 + 共同爱好数量加成
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        double jaccard = (double) common.size() / union.size();
        double commonBonus = Math.min(common.size() * 0.1, 0.3); // 最多加 0.3

        return Math.min(jaccard + commonBonus, 1.0);
    }

    /**
     * 获取共同爱好列表
     */
    private List<String> getCommonInterests(List<String> interests1, List<String> interests2) {
        if (interests1 == null || interests2 == null) {
            return new ArrayList<>();
        }
        return interests1.stream()
                .filter(interests2::contains)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有可用用户（在线用户 + 匹配队列中的用户）
     */
    private Flux<User> getAllAvailableUsers(String excludeUserId) {
        // 1. 获取在线用户
        Flux<User> onlineUsers = redisTemplate.keys(ONLINE_KEY_PREFIX + "*")
                .map(key -> key.replace(ONLINE_KEY_PREFIX, ""))
                .filter(userId -> !userId.equals(excludeUserId))
                .flatMap(userRepository::findById);

        // 2. 获取匹配队列中的用户
        Flux<User> queueUsers = redisTemplate.opsForSet()
                .members(MATCHING_QUEUE_KEY)
                .filter(userId -> !userId.equals(excludeUserId))
                .flatMap(userRepository::findById);

        // 合并两个流并去重
        return Flux.concat(onlineUsers, queueUsers)
                .distinct(User::getId);
    }

    /**
     * 创建匹配结果
     */
    private Mono<MatchResult> createMatchResult(String userId, MatchCandidate match) {
        return redisTemplate.opsForSet()
                .remove(MATCHING_QUEUE_KEY, userId)
                .then(redisTemplate.opsForSet().remove(MATCHING_QUEUE_KEY, match.getUser().getId()))
                .thenReturn(MatchResult.builder()
                        .matchedUser(match.getUser())
                        .matchScore(match.getTotalScore())
                        .matchLevel(match.getLevel())
                        .personalityCompatibility(match.getPersonalityScore())
                        .interestCompatibility(match.getInterestScore())
                        .commonInterests(match.getCommonInterests())
                        .matchReason(generateMatchReason(match))
                        .build())
                .doOnSuccess(result -> log.info("用户 {} 匹配到 {}，匹配度: {}%，等级: {}",
                        userId, match.getUser().getId(),
                        String.format("%.1f", match.getTotalScore() * 100),
                        match.getLevel()));
    }

    /**
     * 生成匹配原因描述
     */
    private String generateMatchReason(MatchCandidate match) {
        StringBuilder reason = new StringBuilder();

        switch (match.getLevel()) {
            case SOULMATE:
                reason.append("✨ 灵魂级匹配！你们的人格高度契合");
                break;
            case HIGH:
                reason.append("🎯 高度匹配！你们的性格非常合拍");
                break;
            case MEDIUM:
                reason.append("💫 不错的匹配！你们有互补的特质");
                break;
            default:
                reason.append("🌟 新的缘分！不妨认识一下");
        }

        if (!match.getCommonInterests().isEmpty()) {
            reason.append("，共同爱好：")
                    .append(String.join("、", match.getCommonInterests().subList(0,
                            Math.min(3, match.getCommonInterests().size()))));
        }

        return reason.toString();
    }

    /**
     * 加入匹配队列
     */
    private Mono<Void> addToMatchingQueue(String userId) {
        return redisTemplate.opsForSet()
                .add(MATCHING_QUEUE_KEY, userId)
                .then();
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

    // ==================== 内部类定义 ====================

    /**
     * 匹配等级枚举
     */
    public enum MatchLevel {
        SOULMATE("灵魂伴侣", "你们的人格高度契合，是难得的灵魂伴侣"),
        HIGH("高度匹配", "你们的性格和爱好都很合拍"),
        MEDIUM("中等匹配", "你们有互补的特质，值得深入了解"),
        LOW("普通匹配", "新的缘分，不妨认识一下");

        private final String label;
        private final String description;

        MatchLevel(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() { return label; }
        public String getDescription() { return description; }
    }

    /**
     * 匹配候选者
     */
    @lombok.Data
    @lombok.Builder
    private static class MatchCandidate {
        private User user;
        private double personalityScore;
        private double interestScore;
        private double totalScore;
        private MatchLevel level;
        private List<String> commonInterests;
    }

    /**
     * 匹配结果
     */
    @lombok.Data
    @lombok.Builder
    public static class MatchResult {
        private User matchedUser;
        private double matchScore;
        private MatchLevel matchLevel;
        private double personalityCompatibility;
        private double interestCompatibility;
        private List<String> commonInterests;
        private String matchReason;

        public String getMatchLevelLabel() {
            return matchLevel != null ? matchLevel.getLabel() : "未知";
        }

        public String getMatchLevelDescription() {
            return matchLevel != null ? matchLevel.getDescription() : "";
        }
    }
}
