package com.talkingai.soulchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResponse {
    private Boolean success;
    private Integer answeredCount;
    private Integer totalQuestions;
    private Boolean isCompleted;
    private NextQuestionDTO nextQuestion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextQuestionDTO {
        private Integer questionNumber;
        private String content;
        private String dimension;
        private java.util.List<StartAssessmentResponse.OptionDTO> options;
    }
}
