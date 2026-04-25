package com.talkingai.soulchat.dto;

import com.talkingai.soulchat.entity.QuestionTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAssessmentResponse {
    private String sessionId;
    private Integer totalQuestions;
    private Integer currentQuestion;
    private QuestionDTO question;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDTO {
        private Integer questionNumber;
        private String content;
        private String dimension;
        private List<OptionDTO> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDTO {
        private String label;
        private String text;
    }
}
