package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_profiles")
public class UserProfile extends BaseEntity {

    @Indexed(unique = true)
    private String userId;

    private String bio;

    private List<String> interests;

    private String gender;

    private Integer age;

    private String location;

    private String occupation;

    private String education;

    private List<String> lookingFor;

    private List<String> photos;

    private SocialLinks socialLinks;

    private PrivacySettings privacy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialLinks {
        private String wechat;
        private String qq;
        private String weibo;
        private String instagram;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacySettings {
        private Boolean showAge;
        private Boolean showLocation;
        private Boolean showOccupation;
    }
}
