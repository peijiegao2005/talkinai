package com.talkingai.soulchat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentSessionService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "assessment:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    @Data
    public static class AssessmentSession {
        private String userId;
        private String sessionId;
        private Long startTime;
        private List<Answer> answers;
        private Integer currentQuestionIndex;
        private Boolean completed;

        public AssessmentSession() {
            this.answers = new ArrayList<>();
            this.currentQuestionIndex = 0;
            this.completed = false;
            this.startTime = System.currentTimeMillis();
        }
    }

    @Data
    public static class Answer {
        private Integer questionNumber;
        private String dimension;
        private Integer selectedScore;
        private Long answerTime;
    }

    public Mono<AssessmentSession> createSession(String userId) {
        String sessionId = generateSessionId(userId);
        AssessmentSession session = new AssessmentSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);

        return saveSession(session)
                .doOnSuccess(s -> log.info("Created assessment session for user: {}", userId))
                .doOnError(e -> log.error("Failed to create session for user: {}", userId, e));
    }

    public Mono<AssessmentSession> getSession(String userId) {
        String key = SESSION_KEY_PREFIX + userId;
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        AssessmentSession session = objectMapper.readValue(json, AssessmentSession.class);
                        return Mono.just(session);
                    } catch (Exception e) {
                        log.error("Failed to deserialize session for user: {}", userId, e);
                        return Mono.error(e);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Session not found for user: {}", userId);
                    return Mono.empty();
                }));
    }

    public Mono<AssessmentSession> saveAnswer(String userId, Integer questionNumber, String dimension, Integer score) {
        return getSession(userId)
                .flatMap(session -> {
                    Answer answer = new Answer();
                    answer.setQuestionNumber(questionNumber);
                    answer.setDimension(dimension);
                    answer.setSelectedScore(score);
                    answer.setAnswerTime(System.currentTimeMillis());

                    session.getAnswers().add(answer);
                    session.setCurrentQuestionIndex(questionNumber);

                    return saveSession(session);
                });
    }

    public Mono<AssessmentSession> completeSession(String userId) {
        return getSession(userId)
                .flatMap(session -> {
                    session.setCompleted(true);
                    return saveSession(session);
                });
    }

    public Mono<Void> deleteSession(String userId) {
        String key = SESSION_KEY_PREFIX + userId;
        return redisTemplate.delete(key)
                .doOnSuccess(count -> log.info("Deleted session for user: {}, count: {}", userId, count))
                .then();
    }

    public Mono<Map<String, Double>> calculateDimensionScores(String userId) {
        return getSession(userId)
                .map(session -> {
                    Map<String, List<Integer>> dimensionScores = new HashMap<>();

                    for (Answer answer : session.getAnswers()) {
                        dimensionScores
                                .computeIfAbsent(answer.getDimension(), k -> new ArrayList<>())
                                .add(answer.getSelectedScore());
                    }

                    Map<String, Double> result = new HashMap<>();
                    for (Map.Entry<String, List<Integer>> entry : dimensionScores.entrySet()) {
                        double avg = entry.getValue().stream()
                                .mapToInt(Integer::intValue)
                                .average()
                                .orElse(0.0);
                        result.put(entry.getKey(), avg);
                    }

                    return result;
                });
    }

    private Mono<AssessmentSession> saveSession(AssessmentSession session) {
        String key = SESSION_KEY_PREFIX + session.getUserId();
        try {
            String json = objectMapper.writeValueAsString(session);
            return redisTemplate.opsForValue()
                    .set(key, json, SESSION_TTL)
                    .thenReturn(session);
        } catch (Exception e) {
            log.error("Failed to serialize session for user: {}", session.getUserId(), e);
            return Mono.error(e);
        }
    }

    private String generateSessionId(String userId) {
        return userId + "_" + System.currentTimeMillis();
    }
}
