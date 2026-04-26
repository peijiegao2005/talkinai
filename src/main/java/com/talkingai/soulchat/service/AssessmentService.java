package com.talkingai.soulchat.service;

import com.talkingai.soulchat.dto.AnswerResponse;
import com.talkingai.soulchat.dto.AssessmentResultResponse;
import com.talkingai.soulchat.dto.StartAssessmentResponse;
import com.talkingai.soulchat.entity.AssessmentReport;
import com.talkingai.soulchat.entity.QuestionTemplate;
import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.repository.AssessmentReportRepository;
import com.talkingai.soulchat.repository.QuestionTemplateRepository;
import com.talkingai.soulchat.repository.UserRepository;
import com.talkingai.soulchat.service.AssessmentSessionService.AssessmentSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentSessionService sessionService;
    private final QuestionTemplateRepository questionTemplateRepository;
    private final AssessmentReportRepository assessmentReportRepository;
    private final UserRepository userRepository;
    private final LlmService llmService;

    private static final int TOTAL_QUESTIONS = 10;
    private static final String[] DIMENSIONS = {
            "extraversion", "openness", "agreeableness",
            "conscientiousness", "emotional_stability"
    };

    public Mono<StartAssessmentResponse> startAssessment(String userId) {
        return sessionService.createSession(userId)
                .flatMap(session ->
                    questionTemplateRepository.findByQuestionNumber(1)
                            .map(question -> buildStartResponse(session, question))
                );
    }

    public Mono<StartAssessmentResponse> restartAssessment(String userId) {
        // 清除现有会话（如果存在），重新开始
        return sessionService.deleteSession(userId)
                .onErrorResume(e -> {
                    log.warn("删除会话失败（可能不存在）: {}", e.getMessage());
                    return Mono.empty();
                })
                .then(sessionService.createSession(userId))
                .flatMap(session ->
                    questionTemplateRepository.findByQuestionNumber(1)
                            .map(question -> buildStartResponse(session, question))
                )
                .doOnSuccess(r -> log.info("Assessment restarted for user: {}", userId));
    }

    public Mono<AnswerResponse> submitAnswer(String userId, Integer questionNumber, Integer selectedScore) {
        return questionTemplateRepository.findByQuestionNumber(questionNumber)
                .flatMap(question -> {
                    String dimension = question.getDimension();
                    return sessionService.saveAnswer(userId, questionNumber, dimension, selectedScore)
                            .flatMap(session -> {
                                int nextQuestionNum = questionNumber + 1;
                                boolean isCompleted = nextQuestionNum > TOTAL_QUESTIONS;

                                if (isCompleted) {
                                    return sessionService.completeSession(userId)
                                            .then(buildAnswerResponse(session, null, true));
                                }

                                return questionTemplateRepository.findByQuestionNumber(nextQuestionNum)
                                        .flatMap(nextQuestion ->
                                            buildAnswerResponse(session, nextQuestion, false)
                                        );
                            });
                });
    }

    public Mono<AssessmentResultResponse> getAssessmentResult(String userId) {
        return sessionService.calculateDimensionScores(userId)
                .flatMap(dimensionScores -> {
                    List<Double> personalityVector = buildPersonalityVector(dimensionScores);

                    // 使用LLM生成详细报告
                    return generateAiReport(userId, dimensionScores, personalityVector)
                            .flatMap(aiReport -> saveAssessmentReport(userId, dimensionScores, personalityVector,
                                    aiReport.getSummary(), aiReport.getDescription())
                                    .then(updateUserPersonalityVector(userId, personalityVector))
                                    .then(sessionService.deleteSession(userId))
                                    .thenReturn(AssessmentResultResponse.builder()
                                            .completed(true)
                                            .dimensionScores(dimensionScores)
                                            .personalityVector(personalityVector)
                                            .summary(aiReport.getSummary())
                                            .description(aiReport.getDescription())
                                            .build()));
                });
    }

    private Mono<AiReport> generateAiReport(String userId, Map<String, Double> dimensionScores, List<Double> personalityVector) {
        return userRepository.findById(userId)
                .flatMap(user -> llmService.generateDetailedAnalysis(
                        user.getNickname() != null ? user.getNickname() : "用户",
                        dimensionScores))
                .map(aiContent -> {
                    AiReport report = new AiReport();
                    report.setSummary(generateSummary(dimensionScores));
                    report.setDescription(aiContent);
                    return report;
                })
                .onErrorResume(e -> {
                    log.warn("LLM生成报告失败，使用默认描述: {}", e.getMessage());
                    AiReport report = new AiReport();
                    report.setSummary(generateSummary(dimensionScores));
                    report.setDescription(generateDescription(dimensionScores));
                    return Mono.just(report);
                });
    }

    @Data
    private static class AiReport {
        private String summary;
        private String description;
    }

    public Mono<AssessmentResultResponse> getLatestReport(String userId) {
        return assessmentReportRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .next()
                .map(report -> AssessmentResultResponse.builder()
                        .completed(true)
                        .dimensionScores(report.getDimensionScores())
                        .personalityVector(report.getPersonalityVector())
                        .summary(report.getSummary())
                        .description(report.getDescription())
                        .build())
                .switchIfEmpty(Mono.just(AssessmentResultResponse.builder()
                        .completed(false)
                        .build()));
    }

    private StartAssessmentResponse buildStartResponse(AssessmentSession session, QuestionTemplate question) {
        return StartAssessmentResponse.builder()
                .sessionId(session.getSessionId())
                .totalQuestions(TOTAL_QUESTIONS)
                .currentQuestion(1)
                .question(mapToQuestionDTO(question))
                .build();
    }

    private Mono<AnswerResponse> buildAnswerResponse(AssessmentSession session,
                                                      QuestionTemplate nextQuestion,
                                                      boolean isCompleted) {
        AnswerResponse.NextQuestionDTO nextQuestionDTO = null;

        if (nextQuestion != null) {
            nextQuestionDTO = AnswerResponse.NextQuestionDTO.builder()
                    .questionNumber(nextQuestion.getQuestionNumber())
                    .content(nextQuestion.getContent())
                    .dimension(nextQuestion.getDimension())
                    .options(nextQuestion.getOptions().stream()
                            .map(opt -> StartAssessmentResponse.OptionDTO.builder()
                                    .label(opt.getLabel())
                                    .text(opt.getText())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
        }

        return Mono.just(AnswerResponse.builder()
                .success(true)
                .answeredCount(session.getAnswers().size())
                .totalQuestions(TOTAL_QUESTIONS)
                .isCompleted(isCompleted)
                .nextQuestion(nextQuestionDTO)
                .build());
    }

    private StartAssessmentResponse.QuestionDTO mapToQuestionDTO(QuestionTemplate question) {
        return StartAssessmentResponse.QuestionDTO.builder()
                .questionNumber(question.getQuestionNumber())
                .content(question.getContent())
                .dimension(question.getDimension())
                .options(question.getOptions().stream()
                        .map(opt -> StartAssessmentResponse.OptionDTO.builder()
                                .label(opt.getLabel())
                                .text(opt.getText())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private List<Double> buildPersonalityVector(Map<String, Double> dimensionScores) {
        List<Double> vector = new ArrayList<>();
        for (String dimension : DIMENSIONS) {
            Double score = dimensionScores.getOrDefault(dimension, 3.0);
            // 归一化到 -1 到 1 之间
            double normalized = (score - 3.0) / 2.0;
            vector.add(normalized);
        }
        return vector;
    }

    private String generateSummary(Map<String, Double> scores) {
        StringBuilder summary = new StringBuilder();
        summary.append("你的性格特征：");

        if (scores.getOrDefault("extraversion", 3.0) > 3.5) {
            summary.append("外向活泼，喜欢社交；");
        } else if (scores.getOrDefault("extraversion", 3.0) < 2.5) {
            summary.append("内向沉稳，享受独处；");
        }

        if (scores.getOrDefault("openness", 3.0) > 3.5) {
            summary.append("富有创意，乐于尝试新事物；");
        }

        if (scores.getOrDefault("conscientiousness", 3.0) > 3.5) {
            summary.append("做事认真负责，有条理；");
        }

        if (scores.getOrDefault("agreeableness", 3.0) > 3.5) {
            summary.append("善解人意，乐于助人。");
        }

        return summary.toString();
    }

    private String generateDescription(Map<String, Double> scores) {
        return "基于大五人格理论分析，你在五个维度上展现出独特的性格组合。" +
                "这种性格特征会影响你的社交方式、决策风格和人际关系。" +
                "系统会根据这些特征为你匹配性格互补或相似的朋友。";
    }

    private Mono<AssessmentReport> saveAssessmentReport(String userId,
                                                         Map<String, Double> dimensionScores,
                                                         List<Double> personalityVector,
                                                         String summary,
                                                         String description) {
        AssessmentReport report = AssessmentReport.builder()
                .userId(userId)
                .dimensionScores(dimensionScores)
                .personalityVector(personalityVector)
                .summary(summary)
                .description(description)
                .build();

        return assessmentReportRepository.save(report)
                .doOnSuccess(r -> log.info("Saved assessment report for user: {}", userId));
    }

    private Mono<User> updateUserPersonalityVector(String userId, List<Double> personalityVector) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    user.setPersonalityVector(personalityVector);
                    user.setHasAssessment(true);
                    return userRepository.save(user);
                })
                .doOnSuccess(u -> log.info("Updated personality vector for user: {}", userId));
    }
}
