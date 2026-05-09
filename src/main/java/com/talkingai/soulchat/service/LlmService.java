package com.talkingai.soulchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.model:gpt-3.5-turbo}")
    private String model;

    private final ObjectMapper objectMapper;

    private WebClient getWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Mono<String> generatePersonalityReport(Map<String, Double> dimensionScores, List<Double> personalityVector) {
        String prompt = buildPersonalityPrompt(dimensionScores, personalityVector);
        return callLlm(prompt);
    }

    public Mono<String> generateDetailedAnalysis(String username, Map<String, Double> scores) {
        String prompt = String.format(
                "请为用户 '%s' 生成一份详细的人格分析报告。\n\n" +
                "大五人格维度得分（1-5分）:\n" +
                "- 外向性(Extraversion): %.1f\n" +
                "- 开放性(Openness): %.1f\n" +
                "- 宜人性(Agreeableness): %.1f\n" +
                "- 尽责性(Conscientiousness): %.1f\n" +
                "- 情绪稳定性(Emotional Stability): %.1f\n\n" +
                "请从以下方面分析:\n" +
                "1. 人格特质概述\n" +
                "2. 社交风格分析\n" +
                "3. 适合的交友类型\n" +
                "4. 个性化建议\n\n" +
                "用中文回答，语气友好专业。",
                username,
                scores.getOrDefault("extraversion", 3.0),
                scores.getOrDefault("openness", 3.0),
                scores.getOrDefault("agreeableness", 3.0),
                scores.getOrDefault("conscientiousness", 3.0),
                scores.getOrDefault("emotional_stability", 3.0)
        );

        return callLlm(prompt);
    }

    public Mono<String> generateMatchAdvice(String user1Name, String user2Name,
                                             Map<String, Double> user1Scores,
                                             Map<String, Double> user2Scores) {
        String prompt = String.format(
                "请为两位用户的匹配生成建议。\n\n" +
                "用户 '%s' 的人格特质:\n" +
                "- 外向性: %.1f, 开放性: %.1f\n\n" +
                "用户 '%s' 的人格特质:\n" +
                "- 外向性: %.1f, 开放性: %.1f\n\n" +
                "请分析:\n" +
                "1. 两人的性格互补性\n" +
                "2. 可能的共同话题\n" +
                "3. 相处建议\n\n" +
                "用中文回答，语气友好。",
                user1Name,
                user1Scores.getOrDefault("extraversion", 3.0),
                user1Scores.getOrDefault("openness", 3.0),
                user2Name,
                user2Scores.getOrDefault("extraversion", 3.0),
                user2Scores.getOrDefault("openness", 3.0)
        );

        return callLlm(prompt);
    }

    private Mono<String> callLlm(String prompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("LLM API Key未配置，返回模拟响应");
            return Mono.just(generateMockResponse(prompt));
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个专业的人格分析助手，擅长基于大五人格理论进行分析。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 1000
        );

        return getWebClient()
                .post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::extractContent)
                .doOnError(e -> log.error("LLM调用失败: {}", e.getMessage()))
                .onErrorReturn("AI分析服务暂时不可用，请稍后再试。");
    }

    /**
     * 支持对话历史的AI调用
     * @param messages 对话历史，包含role和content
     * @return AI回复内容
     * @throws RuntimeException 当API未配置或调用失败时抛出异常
     */
    public Mono<String> chatWithHistory(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("LLM API Key未配置，无法调用AI服务");
            return Mono.error(new RuntimeException("AI服务未配置"));
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.8,
                "max_tokens", 800
        );

        return getWebClient()
                .post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::extractContent)
                .doOnError(e -> log.error("LLM对话调用失败: {}", e.getMessage()));
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("解析LLM响应失败: {}", e.getMessage());
            return "解析响应失败";
        }
    }

    private String buildPersonalityPrompt(Map<String, Double> dimensionScores, List<Double> personalityVector) {
        return String.format(
                "基于以下人格数据生成详细报告:\n" +
                "维度得分: %s\n" +
                "人格向量: %s\n\n" +
                "请生成包含以下内容的JSON格式报告:\n" +
                "1. summary - 一句话总结\n" +
                "2. strengths - 优势特点(数组)\n" +
                "3. weaknesses - 待改进方面(数组)\n" +
                "4. socialTips - 社交建议(数组)",
                dimensionScores.toString(),
                personalityVector.toString()
        );
    }

    private String generateMockResponse(String prompt) {
        if (prompt.contains("详细的人格分析报告")) {
            return "根据您的大五人格测评结果，您是一个平衡且富有同理心的人。\n\n" +
                   "**人格特质概述**:\n" +
                   "您在宜人性方面表现突出，这意味着您善解人意、乐于助人，在人际交往中能够很好地理解他人的感受。\n\n" +
                   "**社交风格分析**:\n" +
                   "您倾向于建立深度而非广度的社交关系，喜欢有意义的对话而非表面寒暄。\n\n" +
                   "**适合的交友类型**:\n" +
                   "您适合与同样重视真诚交流的人建立友谊，特别是那些愿意分享内心世界的朋友。\n\n" +
                   "**个性化建议**:\n" +
                   "1. 在社交场合中，可以尝试主动发起话题\n" +
                   "2. 保持开放心态，接纳不同性格类型的朋友\n" +
                   "3. 在帮助他人的同时，也要关注自己的需求";
        }
        return "AI分析服务响应";
    }
}
