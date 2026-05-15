package com.talkingai.soulchat.service;

import com.talkingai.soulchat.config.AiPromptTemplates;
import com.talkingai.soulchat.entity.MoodAssessment;
import com.talkingai.soulchat.entity.MoodQuestion;
import com.talkingai.soulchat.entity.MoodRoom;
import com.talkingai.soulchat.repository.MoodAssessmentRepository;
import com.talkingai.soulchat.repository.MoodQuestionRepository;
import com.talkingai.soulchat.repository.MoodRoomRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI对话式心情评测服务
 * 支持AI自由对话评估，保留选择题作为降级方案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMoodAssessmentService {

    private final LlmService llmService;
    private final MoodAssessmentRepository assessmentRepository;
    private final MoodRoomRepository roomRepository;
    private final MoodQuestionRepository questionRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    // 内存中存储进行中的AI评测会话
    private final Map<String, AiMoodSession> activeSessions = new ConcurrentHashMap<>();

    private static final int MAX_AI_ROUNDS = 5;
    private static final int MAX_DAILY_AI_ASSESSMENTS = 5;
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(8);
    private static final String AI_LIMIT_KEY_PREFIX = "mood_ai_limit:";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 解析AI回复中的评估标记
    private static final Pattern ASSESSMENT_PATTERN = Pattern.compile(
            "\\[ASSESSMENT:([^:]+):([^:]+):([^\\]]+)\\]"
    );

    @Value("${llm.api-key:}")
    private String llmApiKey;

    /**
     * 开始AI心情评测
     * 首先检查API配置和每日限制，然后初始化AI对话
     */
    public Mono<StartAssessmentResult> startAiAssessment(String userId) {
        // 首先检查AI是否可用（API Key是否配置）
        if (!isAiConfigured()) {
            log.info("AI服务未配置，用户 {} 直接使用选择题模式", userId);
            return startFallbackAssessment(userId);
        }

        // AI已配置，检查每日限制
        String today = LocalDate.now().format(DATE_FORMATTER);
        String limitKey = AI_LIMIT_KEY_PREFIX + userId + ":" + today;

        return checkAndIncrementAiLimit(limitKey)
                .flatMap(canProceed -> {
                    if (!canProceed) {
                        // 超过每日限制，降级到选择题模式
                        log.info("用户 {} AI评估次数已达上限，降级到选择题模式", userId);
                        return startFallbackAssessment(userId);
                    }
                    return startAiDialogAssessment(userId);
                });
    }

    /**
     * 检查AI服务是否已配置
     */
    private boolean isAiConfigured() {
        boolean configured = llmApiKey != null && !llmApiKey.isEmpty();
        log.info("AI服务配置检查: API Key {}配置", configured ? "已" : "未");
        if (configured) {
            log.info("API Key 前缀: {}...", llmApiKey.substring(0, Math.min(10, llmApiKey.length())));
        }
        return configured;
    }

    /**
     * 检查并增加AI评估次数
     */
    private Mono<Boolean> checkAndIncrementAiLimit(String limitKey) {
        return redisTemplate.opsForValue()
                .get(limitKey)
                .defaultIfEmpty("0")
                .map(Integer::parseInt)
                .flatMap(currentCount -> {
                    if (currentCount >= MAX_DAILY_AI_ASSESSMENTS) {
                        return Mono.just(false);
                    }
                    // 增加计数，设置当天过期
                    return redisTemplate.opsForValue()
                            .increment(limitKey)
                            .flatMap(newCount -> {
                                if (newCount == 1) {
                                    // 第一次设置，添加过期时间
                                    return redisTemplate.expire(limitKey, Duration.ofHours(24))
                                            .thenReturn(true);
                                }
                                return Mono.just(true);
                            });
                });
    }

    /**
     * 启动AI对话式评估
     */
    private Mono<StartAssessmentResult> startAiDialogAssessment(String userId) {
        String sessionId = UUID.randomUUID().toString();

        AiMoodSession session = AiMoodSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .currentRound(1)
                .messages(new ArrayList<>())
                .assessmentType(AssessmentType.AI_DIALOG)
                .build();

        // 添加系统提示词
        session.getMessages().add(Map.of(
                "role", "system",
                "content", AiPromptTemplates.MOOD_ASSESSMENT_SYSTEM_PROMPT
        ));

        // 添加第一轮提示
        session.getMessages().add(Map.of(
                "role", "user",
                "content", AiPromptTemplates.MOOD_ASSESSMENT_ROUND1_PROMPT
        ));

        activeSessions.put(sessionId, session);

        // 调用AI获取第一轮回复（带超时）
        return callAiWithTimeout(session.getMessages())
                .flatMap(aiResponse -> {
                    session.getMessages().add(Map.of(
                            "role", "assistant",
                            "content", aiResponse
                    ));

                    return Mono.just(StartAssessmentResult.builder()
                            .sessionId(sessionId)
                            .assessmentType(AssessmentType.AI_DIALOG)
                            .currentRound(1)
                            .totalRounds(MAX_AI_ROUNDS)
                            .aiMessage(aiResponse)
                            .build());
                })
                .onErrorResume(e -> {
                    log.error("AI对话启动失败，降级到选择题模式");
                    log.error("错误类型: {}", e.getClass().getName());
                    log.error("错误信息: {}", e.getMessage());
                    if (e.getCause() != null) {
                        log.error("根本原因: {}", e.getCause().getMessage());
                    }
                    activeSessions.remove(sessionId);
                    return startFallbackAssessment(userId);
                });
    }

    /**
     * 降级到选择题模式
     */
    private Mono<StartAssessmentResult> startFallbackAssessment(String userId) {
        String sessionId = UUID.randomUUID().toString();

        AiMoodSession session = AiMoodSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .currentRound(1)
                .answers(new HashMap<>())
                .assessmentType(AssessmentType.CHOICE_QUESTIONS)
                .build();

        activeSessions.put(sessionId, session);

        return questionRepository.findByQuestionNumber(1)
                .map(question -> StartAssessmentResult.builder()
                        .sessionId(sessionId)
                        .assessmentType(AssessmentType.CHOICE_QUESTIONS)
                        .currentRound(1)
                        .totalRounds(5)
                        .question(QuestionDTO.builder()
                                .questionNumber(question.getQuestionNumber())
                                .content(question.getContent())
                                .options(question.getOptions())
                                .build())
                        .build());
    }

    /**
     * 提交AI对话回复
     */
    public Mono<DialogResponse> submitDialogMessage(String sessionId, String userMessage) {
        AiMoodSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.error(new RuntimeException("会话已过期，请重新开始"));
        }

        if (session.getAssessmentType() != AssessmentType.AI_DIALOG) {
            return Mono.error(new RuntimeException("当前不是AI对话模式"));
        }

        // 添加用户消息
        session.getMessages().add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        int nextRound = session.getCurrentRound() + 1;

        // 如果是最后一轮，添加最终评估提示
        if (nextRound >= MAX_AI_ROUNDS) {
            session.getMessages().add(Map.of(
                    "role", "user",
                    "content", AiPromptTemplates.MOOD_ASSESSMENT_FINAL_PROMPT
            ));
        }

        // 调用AI（带超时）
        return callAiWithTimeout(session.getMessages())
                .flatMap(aiResponse -> {
                    session.getMessages().add(Map.of(
                            "role", "assistant",
                            "content", aiResponse
                    ));
                    session.setCurrentRound(nextRound);

                    // 检查是否是最后一轮（包含评估标记）
                    if (nextRound >= MAX_AI_ROUNDS) {
                        return processFinalAssessment(session, aiResponse);
                    }

                    return Mono.just(DialogResponse.builder()
                            .completed(false)
                            .currentRound(nextRound)
                            .totalRounds(MAX_AI_ROUNDS)
                            .aiMessage(aiResponse)
                            .build());
                })
                .onErrorResume(e -> {
                    log.warn("AI对话失败，降级到选择题模式: {}", e.getMessage());
                    return switchToFallbackMode(session);
                });
    }

    /**
     * 处理最终评估结果
     */
    private Mono<DialogResponse> processFinalAssessment(AiMoodSession session, String aiResponse) {
        // 解析评估标记
        Matcher matcher = ASSESSMENT_PATTERN.matcher(aiResponse);
        String primaryMood = "neutral";
        int intensity = 5;
        String recommendedRoomId = "daily_chat";

        if (matcher.find()) {
            primaryMood = matcher.group(1).trim();
            try {
                intensity = Integer.parseInt(matcher.group(2).trim());
            } catch (NumberFormatException e) {
                log.warn("解析情绪强度失败: {}", matcher.group(2));
            }
            recommendedRoomId = matcher.group(3).trim();
        }

        // 验证房间ID有效性
        final String finalRoomId = validateRoomId(recommendedRoomId);
        final String finalMood = primaryMood;
        final int finalIntensity = intensity;

        // 保存评估结果
        MoodAssessment assessment = MoodAssessment.builder()
                .userId(session.getUserId())
                .sessionId(session.getSessionId())
                .primaryMood(finalMood)
                .moodIntensity(finalIntensity)
                .recommendedRoomId(finalRoomId)
                .completed(true)
                .build();

        // 获取房间信息
        final String aiMessageClean = aiResponse.replaceAll("\\[ASSESSMENT:[^\\]]+\\]", "").trim();

        return assessmentRepository.save(assessment)
                .flatMap(saved -> roomRepository.findByRoomId(finalRoomId))
                .map(room -> DialogResponse.builder()
                        .completed(true)
                        .currentRound(MAX_AI_ROUNDS)
                        .totalRounds(MAX_AI_ROUNDS)
                        .aiMessage(aiMessageClean)
                        .assessmentResult(AssessmentResultDTO.builder()
                                .primaryMood(finalMood)
                                .intensity(finalIntensity)
                                .recommendedRoomId(room.getRoomId())
                                .recommendedRoomName(room.getName())
                                .recommendedRoomIcon(room.getIcon())
                                .description(room.getDescription())
                                .atmosphere(room.getAtmosphere())
                                .build())
                        .build())
                .doOnSuccess(result -> {
                    activeSessions.remove(session.getSessionId());
                    log.info("用户 {} 完成AI心情评测，主心情: {}，推荐房间: {}",
                            session.getUserId(), finalMood, finalRoomId);
                });
    }

    /**
     * 切换到选择题降级模式
     */
    private Mono<DialogResponse> switchToFallbackMode(AiMoodSession session) {
        session.setAssessmentType(AssessmentType.CHOICE_QUESTIONS);
        session.setCurrentRound(1);
        session.setAnswers(new HashMap<>());

        return questionRepository.findByQuestionNumber(1)
                .map(question -> DialogResponse.builder()
                        .completed(false)
                        .currentRound(1)
                        .totalRounds(5)
                        .fallbackMode(true)
                        .fallbackMessage("AI对话暂时不可用，已切换到选择题模式")
                        .question(QuestionDTO.builder()
                                .questionNumber(question.getQuestionNumber())
                                .content(question.getContent())
                                .options(question.getOptions())
                                .build())
                        .build());
    }

    /**
     * 提交选择题答案（降级模式）
     */
    public Mono<DialogResponse> submitFallbackAnswer(String sessionId, Integer questionNumber, String selectedOption) {
        AiMoodSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.error(new RuntimeException("会话已过期"));
        }

        // 保存答案
        session.getAnswers().put(questionNumber, selectedOption);

        int nextQuestionNum = questionNumber + 1;
        boolean isCompleted = nextQuestionNum > 5;

        if (isCompleted) {
            // 完成选择题评估
            return completeFallbackAssessment(session);
        }

        final int nextNum = nextQuestionNum;
        return questionRepository.findByQuestionNumber(nextNum)
                .map(question -> DialogResponse.builder()
                        .completed(false)
                        .currentRound(nextNum)
                        .totalRounds(5)
                        .fallbackMode(true)
                        .question(QuestionDTO.builder()
                                .questionNumber(question.getQuestionNumber())
                                .content(question.getContent())
                                .options(question.getOptions())
                                .build())
                        .build());
    }

    /**
     * 完成选择题评估
     */
    private Mono<DialogResponse> completeFallbackAssessment(AiMoodSession session) {
        // 使用原有的分析逻辑
        String primaryMood = analyzeFallbackAnswers(session.getAnswers());
        String recommendedRoomId = mapMoodToRoom(primaryMood);

        MoodAssessment assessment = MoodAssessment.builder()
                .userId(session.getUserId())
                .sessionId(session.getSessionId())
                .answers(session.getAnswers())
                .primaryMood(primaryMood)
                .moodIntensity(5)
                .recommendedRoomId(recommendedRoomId)
                .completed(true)
                .build();

        return assessmentRepository.save(assessment)
                .flatMap(saved -> roomRepository.findByRoomId(recommendedRoomId))
                .map(room -> DialogResponse.builder()
                        .completed(true)
                        .currentRound(5)
                        .totalRounds(5)
                        .fallbackMode(true)
                        .assessmentResult(AssessmentResultDTO.builder()
                                .primaryMood(primaryMood)
                                .intensity(5)
                                .recommendedRoomId(room.getRoomId())
                                .recommendedRoomName(room.getName())
                                .recommendedRoomIcon(room.getIcon())
                                .description(room.getDescription())
                                .atmosphere(room.getAtmosphere())
                                .build())
                        .build())
                .doOnSuccess(result -> activeSessions.remove(session.getSessionId()));
    }

    /**
     * 调用AI（带8秒超时）
     */
    private Mono<String> callAiWithTimeout(List<Map<String, String>> messages) {
        return llmService.chatWithHistory(messages)
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(AI_TIMEOUT)
                .onErrorMap(throwable -> {
                    if (throwable instanceof java.util.concurrent.TimeoutException) {
                        return new RuntimeException("AI响应超时");
                    }
                    return throwable;
                });
    }

    /**
     * 验证房间ID有效性
     */
    private String validateRoomId(String roomId) {
        Set<String> validRooms = Set.of(
                "happy_station", "anxiety_hole", "peaceful_island",
                "inspiration_spark", "lonely_star", "daily_chat"
        );
        return validRooms.contains(roomId) ? roomId : "daily_chat";
    }

    /**
     * 分析选择题答案
     */
    private String analyzeFallbackAnswers(Map<Integer, String> answers) {
        // 简化的分析逻辑
        String answer1 = answers.getOrDefault(1, "C");
        return switch (answer1) {
            case "A" -> "happy";
            case "B" -> "peaceful";
            case "C" -> "anxious";
            case "D" -> "lonely";
            case "E" -> "neutral";
            default -> "neutral";
        };
    }

    /**
     * 映射心情到房间
     */
    private String mapMoodToRoom(String mood) {
        return switch (mood) {
            case "happy", "excited" -> "happy_station";
            case "anxious", "nervous" -> "anxiety_hole";
            case "peaceful", "relaxed" -> "peaceful_island";
            case "curious", "interested" -> "inspiration_spark";
            case "lonely" -> "lonely_star";
            default -> "daily_chat";
        };
    }

    // ==================== DTOs ====================

    @Data
    @Builder
    public static class StartAssessmentResult {
        private String sessionId;
        private AssessmentType assessmentType;
        private Integer currentRound;
        private Integer totalRounds;
        private String aiMessage;           // AI对话模式用
        private QuestionDTO question;       // 选择题模式用
    }

    @Data
    @Builder
    public static class DialogResponse {
        private Boolean completed;
        private Integer currentRound;
        private Integer totalRounds;
        private String aiMessage;
        private QuestionDTO question;
        private Boolean fallbackMode;
        private String fallbackMessage;
        private AssessmentResultDTO assessmentResult;
    }

    @Data
    @Builder
    public static class QuestionDTO {
        private Integer questionNumber;
        private String content;
        private List<MoodQuestion.MoodOption> options;
    }

    @Data
    @Builder
    public static class AssessmentResultDTO {
        private String primaryMood;
        private Integer intensity;
        private String recommendedRoomId;
        private String recommendedRoomName;
        private String recommendedRoomIcon;
        private String description;
        private String atmosphere;
    }

    // ==================== Enums & Internal Classes ====================

    public enum AssessmentType {
        AI_DIALOG,          // AI对话模式
        CHOICE_QUESTIONS    // 选择题模式（降级）
    }

    @Data
    @Builder
    private static class AiMoodSession {
        private String sessionId;
        private String userId;
        private Integer currentRound;
        private AssessmentType assessmentType;
        private List<Map<String, String>> messages;  // AI对话历史
        private Map<Integer, String> answers;        // 选择题答案
    }
}
