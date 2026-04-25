package com.talkingai.soulchat.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "question_templates")
public class QuestionTemplate extends BaseEntity {
    
    private Integer questionNumber;
    
    private String content;
    
    private String dimension;
    
    private List<Option> options;
    
    @Data
    public static class Option {
        private String label;
        private String text;
        private Integer score;
    }
}
