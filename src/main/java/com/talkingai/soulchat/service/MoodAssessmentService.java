package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.MoodAssessment;
import com.talkingai.soulchat.entity.MoodQuestion;
import com.talkingai.soulchat.entity.MoodRoom;
import com.talkingai.soulchat.repository.MoodAssessmentRepository;
import com.talkingai.soulchat.repository.MoodQuestionRepository;
import com.talkingai.soulchat.repository.MoodRoomRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoodAssessmentService {

    private final MoodQuestionRepository questionRepository;
    private final MoodAssessmentRepository assessmentRepository;
    private final MoodRoomRepository roomRepository;

    // 内存中存储进行中的评测会话
    private final Map<String, MoodSession> activeSessions = new ConcurrentHashMap<>();

    private static final int TOTAL_QUESTIONS = 5;

    /**
     * 开始心情评测
     */
    public Mono<MoodSessionResponse> startAssessment(String userId) {
        String sessionId = UUID.randomUUID().toString();

        MoodSession session = new MoodSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setCurrentQuestion(1);
        session.setAnswers(new HashMap<>());
        session.setCompleted(false);

        activeSessions.put(sessionId, session);

        return questionRepository.findByQuestionNumber(1)
                .map(question -> buildSessionResponse(session, question));
    }

    /**
     * 提交答案
     */
    public Mono<AnswerResponse> submitAnswer(String sessionId, Integer questionNumber, String selectedOption) {
        MoodSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.error(new RuntimeException("评测会话已过期，请重新开始"));
        }

        // 保存答案
        session.getAnswers().put(questionNumber, selectedOption);

        int nextQuestionNum = questionNumber + 1;
        boolean isCompleted = nextQuestionNum > TOTAL_QUESTIONS;

        if (isCompleted) {
            session.setCompleted(true);
            return Mono.just(AnswerResponse.builder()
                    .completed(true)
                    .currentQuestion(questionNumber)
                    .totalQuestions(TOTAL_QUESTIONS)
                    .build());
        }

        final int nextNum = nextQuestionNum;
        return questionRepository.findByQuestionNumber(nextNum)
                .map(question -> AnswerResponse.builder()
                        .completed(false)
                        .currentQuestion(nextNum)
                        .totalQuestions(TOTAL_QUESTIONS)
                        .nextQuestion(mapToQuestionDTO(question))
                        .build());
    }

    /**
     * 完成评测并分析结果
     */
    public Mono<MoodResultResponse> completeAssessment(String sessionId) {
        MoodSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Mono.error(new RuntimeException("评测会话已过期"));
        }

        // 分析心情
        MoodAnalysis analysis = analyzeMood(session.getAnswers());

        // 保存评测结果
        MoodAssessment assessment = MoodAssessment.builder()
                .userId(session.getUserId())
                .sessionId(sessionId)
                .answers(session.getAnswers())
                .primaryMood(analysis.getPrimaryMood())
                .secondaryMood(analysis.getSecondaryMood())
                .moodIntensity(analysis.getIntensity())
                .recommendedRoomId(analysis.getRecommendedRoomId())
                .completed(true)
                .build();

        return assessmentRepository.save(assessment)
                .flatMap(saved ->
                    roomRepository.findByRoomId(analysis.getRecommendedRoomId())
                            .map(room -> MoodResultResponse.builder()
                                    .primaryMood(analysis.getPrimaryMood())
                                    .secondaryMood(analysis.getSecondaryMood())
                                    .intensity(analysis.getIntensity())
                                    .recommendedRoomId(room.getRoomId())
                                    .recommendedRoomName(room.getName())
                                    .recommendedRoomIcon(room.getIcon())
                                    .description(room.getDescription())
                                    .atmosphere(room.getAtmosphere())
                                    .build())
                )
                .doOnSuccess(result -> {
                    // 清理会话
                    activeSessions.remove(sessionId);
                    log.info("用户 {} 完成心情评测，主心情: {}，推荐房间: {}",
                            session.getUserId(), analysis.getPrimaryMood(), analysis.getRecommendedRoomId());
                });
    }

    /**
     * 分析心情
     * 基于5道题的答案分析当前心情状态
     */
    private MoodAnalysis analyzeMood(Map<Integer, String> answers) {
        // 心情分数映射
        Map<String, Integer> moodScores = new HashMap<>();
        moodScores.put("happy", 0);
        moodScores.put("excited", 0);
        moodScores.put("satisfied", 0);
        moodScores.put("anxious", 0);
        moodScores.put("nervous", 0);
        moodScores.put("uneasy", 0);
        moodScores.put("peaceful", 0);
        moodScores.put("relaxed", 0);
        moodScores.put("focused", 0);
        moodScores.put("curious", 0);
        moodScores.put("interested", 0);
        moodScores.put("lonely", 0);
        moodScores.put("neutral", 0);

        // 根据答案计算心情分数
        answers.forEach((questionNum, answer) -> {
            switch (questionNum) {
                case 1: // 当前情绪状态
                    updateMoodScore(moodScores, answer, 3);
                    break;
                case 2: // 能量水平
                    updateEnergyMood(moodScores, answer);
                    break;
                case 3: // 社交意愿
                    updateSocialMood(moodScores, answer);
                    break;
                case 4: // 思维状态
                    updateThinkingMood(moodScores, answer);
                    break;
                case 5: // 期望的氛围
                    updateAtmosphereMood(moodScores, answer);
                    break;
            }
        });

        // 找出最高分和次高分的心情
        String primaryMood = moodScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("neutral");

        String secondaryMood = moodScores.entrySet().stream()
                .filter(e -> !e.getKey().equals(primaryMood))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("neutral");

        int intensity = moodScores.get(primaryMood);

        // 映射到推荐房间
        String recommendedRoomId = mapMoodToRoom(primaryMood, secondaryMood);

        MoodAnalysis analysis = new MoodAnalysis();
        analysis.setPrimaryMood(primaryMood);
        analysis.setSecondaryMood(secondaryMood);
        analysis.setIntensity(Math.min(intensity * 10, 100)); // 转换为0-100
        analysis.setRecommendedRoomId(recommendedRoomId);

        return analysis;
    }

    private void updateMoodScore(Map<String, Integer> scores, String mood, int points) {
        scores.merge(mood, points, Integer::sum);
    }

    private void updateEnergyMood(Map<String, Integer> scores, String answer) {
        switch (answer) {
            case "A": // 充满活力
                updateMoodScore(scores, "excited", 3);
                updateMoodScore(scores, "happy", 2);
                break;
            case "B": // 精力充沛
                updateMoodScore(scores, "happy", 3);
                updateMoodScore(scores, "interested", 2);
                break;
            case "C": // 平静稳定
                updateMoodScore(scores, "peaceful", 3);
                updateMoodScore(scores, "relaxed", 2);
                break;
            case "D": // 有些疲惫
                updateMoodScore(scores, "neutral", 2);
                updateMoodScore(scores, "lonely", 1);
                break;
            case "E": // 精疲力尽
                updateMoodScore(scores, "anxious", 2);
                updateMoodScore(scores, "uneasy", 2);
                break;
        }
    }

    private void updateSocialMood(Map<String, Integer> scores, String answer) {
        switch (answer) {
            case "A": // 非常想聊天
                updateMoodScore(scores, "excited", 3);
                updateMoodScore(scores, "happy", 2);
                break;
            case "B": // 愿意交流
                updateMoodScore(scores, "happy", 3);
                updateMoodScore(scores, "interested", 2);
                break;
            case "C": // 看情况
                updateMoodScore(scores, "neutral", 3);
                updateMoodScore(scores, "peaceful", 1);
                break;
            case "D": // 只想倾听
                updateMoodScore(scores, "peaceful", 3);
                updateMoodScore(scores, "relaxed", 2);
                break;
            case "E": // 不想被打扰
                updateMoodScore(scores, "lonely", 3);
                updateMoodScore(scores, "anxious", 1);
                break;
        }
    }

    private void updateThinkingMood(Map<String, Integer> scores, String answer) {
        switch (answer) {
            case "A": // 思维活跃，有很多想法
                updateMoodScore(scores, "curious", 3);
                updateMoodScore(scores, "excited", 2);
                break;
            case "B": // 专注某件事
                updateMoodScore(scores, "focused", 3);
                updateMoodScore(scores, "interested", 2);
                break;
            case "C": // 思绪平静
                updateMoodScore(scores, "peaceful", 3);
                updateMoodScore(scores, "relaxed", 2);
                break;
            case "D": // 有些担忧
                updateMoodScore(scores, "anxious", 3);
                updateMoodScore(scores, "nervous", 2);
                break;
            case "E": // 头脑空白
                updateMoodScore(scores, "neutral", 3);
                updateMoodScore(scores, "lonely", 1);
                break;
        }
    }

    private void updateAtmosphereMood(Map<String, Integer> scores, String answer) {
        switch (answer) {
            case "A": // 热闹欢快
                updateMoodScore(scores, "happy", 3);
                updateMoodScore(scores, "excited", 2);
                break;
            case "B": // 温暖陪伴
                updateMoodScore(scores, "satisfied", 3);
                updateMoodScore(scores, "peaceful", 2);
                break;
            case "C": // 安静舒缓
                updateMoodScore(scores, "peaceful", 3);
                updateMoodScore(scores, "relaxed", 2);
                break;
            case "D": // 深度交流
                updateMoodScore(scores, "curious", 3);
                updateMoodScore(scores, "interested", 2);
                break;
            case "E": // 随意轻松
                updateMoodScore(scores, "neutral", 3);
                updateMoodScore(scores, "relaxed", 1);
                break;
        }
    }

    /**
     * 将心情映射到推荐房间
     */
    private String mapMoodToRoom(String primaryMood, String secondaryMood) {
        // 根据主要心情映射到房间
        switch (primaryMood) {
            case "happy":
            case "excited":
            case "satisfied":
                return "happy_station";
            case "anxious":
            case "nervous":
            case "uneasy":
                return "anxiety_hole";
            case "peaceful":
            case "relaxed":
            case "focused":
                return "peaceful_island";
            case "curious":
            case "interested":
                return "inspiration_spark";
            case "lonely":
                return "lonely_star";
            case "neutral":
            default:
                // 根据次要心情进一步判断
                if (secondaryMood.equals("happy") || secondaryMood.equals("excited")) {
                    return "happy_station";
                } else if (secondaryMood.equals("anxious") || secondaryMood.equals("nervous")) {
                    return "anxiety_hole";
                } else if (secondaryMood.equals("lonely")) {
                    return "lonely_star";
                }
                return "daily_chat";
        }
    }

    private MoodSessionResponse buildSessionResponse(MoodSession session, MoodQuestion question) {
        return MoodSessionResponse.builder()
                .sessionId(session.getSessionId())
                .currentQuestion(session.getCurrentQuestion())
                .totalQuestions(TOTAL_QUESTIONS)
                .question(QuestionDTO.builder()
                        .questionNumber(question.getQuestionNumber())
                        .content(question.getContent())
                        .options(question.getOptions())
                        .build())
                .build();
    }

    private QuestionDTO mapToQuestionDTO(MoodQuestion question) {
        return QuestionDTO.builder()
                .questionNumber(question.getQuestionNumber())
                .content(question.getContent())
                .options(question.getOptions())
                .build();
    }

    // ==================== DTOs ====================

    @Data
    @lombok.Builder
    public static class MoodSessionResponse {
        private String sessionId;
        private Integer currentQuestion;
        private Integer totalQuestions;
        private QuestionDTO question;
    }

    @Data
    @lombok.Builder
    public static class QuestionDTO {
        private Integer questionNumber;
        private String content;
        private List<MoodQuestion.MoodOption> options;
    }

    @Data
    @lombok.Builder
    public static class AnswerResponse {
        private Boolean completed;
        private Integer currentQuestion;
        private Integer totalQuestions;
        private QuestionDTO nextQuestion;
    }

    @Data
    @lombok.Builder
    public static class MoodResultResponse {
        private String primaryMood;
        private String secondaryMood;
        private Integer intensity;
        private String recommendedRoomId;
        private String recommendedRoomName;
        private String recommendedRoomIcon;
        private String description;
        private String atmosphere;
    }

    // ==================== Internal Classes ====================

    @Data
    private static class MoodSession {
        private String sessionId;
        private String userId;
        private Integer currentQuestion;
        private Map<Integer, String> answers;
        private Boolean completed;
    }

    @Data
    private static class MoodAnalysis {
        private String primaryMood;
        private String secondaryMood;
        private Integer intensity;
        private String recommendedRoomId;
    }
}
