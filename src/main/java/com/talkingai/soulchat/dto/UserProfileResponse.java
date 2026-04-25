package com.talkingai.soulchat.dto;

import com.talkingai.soulchat.entity.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
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
    private UserProfile.SocialLinks socialLinks;
    private UserProfile.PrivacySettings privacy;
}
