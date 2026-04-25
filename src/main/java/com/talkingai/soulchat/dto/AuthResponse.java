package com.talkingai.soulchat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private String token;
    private String type;
    private String userId;
    private String username;
    private String nickname;
    private Boolean hasAssessment;
}
