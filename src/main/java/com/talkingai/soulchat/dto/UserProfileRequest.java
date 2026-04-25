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
public class UserProfileRequest {
    private String bio;
    private List<String> interests;
    private String gender;
    private Integer age;
    private String location;
    private String occupation;
    private String education;
    private List<String> lookingFor;
    private List<String> photos;
    private SocialLinksDto socialLinks;
    private PrivacySettingsDto privacy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialLinksDto {
        private String wechat;
        private String qq;
        private String weibo;
        private String instagram;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacySettingsDto {
        private Boolean showAge;
        private Boolean showLocation;
        private Boolean showOccupation;
    }
}
