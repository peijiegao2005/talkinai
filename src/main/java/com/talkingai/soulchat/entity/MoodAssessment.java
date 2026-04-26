package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * 心情评估记录
 * 记录用户每次的心情评测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "mood_assessments")
public class MoodAssessment extends BaseEntity {

    @Indexed
    private String userId;

    private Map<Integer, String> answers;

    private String primaryMood;

    private String secondaryMood;

    private Integer moodIntensity;

    private String recommendedRoomId;

    private String sessionId;

    private Boolean completed;
}
