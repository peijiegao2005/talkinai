package com.talkingai.soulchat.security.content;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final SensitiveWordFilter sensitiveWordFilter;

    public Mono<ModerationResult> moderateText(String content) {
        return Mono.fromCallable(() -> {
            boolean hasSensitiveWord = sensitiveWordFilter.containsSensitiveWord(content);
            List<String> sensitiveWords = sensitiveWordFilter.findSensitiveWords(content);
            String filteredContent = sensitiveWordFilter.filter(content);

            ModerationResult result = new ModerationResult();
            result.setApproved(!hasSensitiveWord);
            result.setOriginalContent(content);
            result.setFilteredContent(filteredContent);
            result.setSensitiveWords(sensitiveWords);
            result.setRiskScore(calculateRiskScore(sensitiveWords.size()));

            if (hasSensitiveWord) {
                log.warn("内容审核发现敏感词: {}", sensitiveWords);
            }

            return result;
        });
    }

    public Mono<String> filterContent(String content) {
        return Mono.fromCallable(() -> sensitiveWordFilter.filter(content));
    }

    private int calculateRiskScore(int sensitiveWordCount) {
        if (sensitiveWordCount == 0) return 0;
        if (sensitiveWordCount <= 2) return 30;
        if (sensitiveWordCount <= 5) return 60;
        return 100;
    }

    public static class ModerationResult {
        private boolean approved;
        private String originalContent;
        private String filteredContent;
        private List<String> sensitiveWords;
        private int riskScore;
        private String reason;

        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }
        public String getOriginalContent() { return originalContent; }
        public void setOriginalContent(String originalContent) { this.originalContent = originalContent; }
        public String getFilteredContent() { return filteredContent; }
        public void setFilteredContent(String filteredContent) { this.filteredContent = filteredContent; }
        public List<String> getSensitiveWords() { return sensitiveWords; }
        public void setSensitiveWords(List<String> sensitiveWords) { this.sensitiveWords = sensitiveWords; }
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
