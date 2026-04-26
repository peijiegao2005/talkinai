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

/**
 * 心情评估问题模板
 * 5道题评估当前心情状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "mood_questions")
public class MoodQuestion extends BaseEntity {

    @Indexed
    private Integer questionNumber;

    private String content;

    private List<MoodOption> options;

    // 选项对应的心情权重映射
    private Map<String, Map<String, Integer>> moodWeights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoodOption {
        private String label;
        private String text;
        private Integer score;
    }
}
