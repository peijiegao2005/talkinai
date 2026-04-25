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
}
