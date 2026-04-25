package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "assessment_reports")
public class AssessmentReport extends BaseEntity {
    
    @Indexed
    private String userId;
    
    private List<Answer> answers;
    
    private Map<String, Double> dimensionScores;
    
    private List<Double> personalityVector;
    
    private String summary;
    
    private String description;
    
    @Data
    public static class Answer {
        private Integer questionNumber;
        private String dimension;
        private Integer selectedOption;
        private Integer score;
    }
}
