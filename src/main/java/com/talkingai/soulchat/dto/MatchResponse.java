package com.talkingai.soulchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {
    private Boolean matched;
    private String matchUserId;
    private String matchNickname;
    private String matchAvatar;
    private String roomId;
    private List<Double> personalityVector;
    private String message;

    // 匹配算法相关字段
    private Double matchScore;              // 总体匹配分数 (0-1)
    private String matchLevel;              // 匹配等级 (灵魂伴侣/高度匹配/中等匹配/普通匹配)
    private String matchLevelDescription;   // 匹配等级描述
    private Double personalityCompatibility; // 人格兼容性分数
    private Double interestCompatibility;   // 爱好兼容性分数
    private List<String> commonInterests;   // 共同爱好列表
    private String matchReason;             // 匹配原因说明
}
