package com.talkingai.soulchat.controller;

import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.dto.UserProfileRequest;
import com.talkingai.soulchat.dto.UserProfileResponse;
import com.talkingai.soulchat.entity.UserProfile;
import com.talkingai.soulchat.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public Mono<ApiResponse<UserProfileResponse>> getMyProfile(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        return userProfileService.getProfile(userId)
                .map(this::convertToResponse)
                .map(ApiResponse::success);
    }

    @GetMapping("/{userId}")
    public Mono<ApiResponse<UserProfileResponse>> getProfile(@PathVariable String userId) {
        return userProfileService.getProfile(userId)
                .map(this::convertToResponse)
                .map(ApiResponse::success);
    }

    @PutMapping
    public Mono<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileRequest request) {
        String userId = authentication.getPrincipal().toString();
        log.info("更新用户资料: {}", userId);

        UserProfile profile = convertToEntity(request);
        return userProfileService.createOrUpdateProfile(userId, profile)
                .map(this::convertToResponse)
                .map(ApiResponse::success);
    }

    @PatchMapping("/bio")
    public Mono<ApiResponse<UserProfileResponse>> updateBio(
            Authentication authentication,
            @RequestParam String bio) {
        String userId = authentication.getPrincipal().toString();
        return userProfileService.updateBio(userId, bio)
                .map(this::convertToResponse)
                .map(ApiResponse::success);
    }

    @PostMapping("/interests")
    public Mono<ApiResponse<UserProfileResponse>> addInterest(
            Authentication authentication,
            @RequestParam String interest) {
        String userId = authentication.getPrincipal().toString();
        return userProfileService.addInterest(userId, interest)
                .map(this::convertToResponse)
                .map(ApiResponse::success);
    }

    private UserProfile convertToEntity(UserProfileRequest request) {
        return UserProfile.builder()
                .bio(request.getBio())
                .interests(request.getInterests())
                .gender(request.getGender())
                .age(request.getAge())
                .location(request.getLocation())
                .occupation(request.getOccupation())
                .education(request.getEducation())
                .lookingFor(request.getLookingFor())
                .photos(request.getPhotos())
                .socialLinks(request.getSocialLinks() != null ?
                        UserProfile.SocialLinks.builder()
                                .wechat(request.getSocialLinks().getWechat())
                                .qq(request.getSocialLinks().getQq())
                                .weibo(request.getSocialLinks().getWeibo())
                                .instagram(request.getSocialLinks().getInstagram())
                                .build() : null)
                .privacy(request.getPrivacy() != null ?
                        UserProfile.PrivacySettings.builder()
                                .showAge(request.getPrivacy().getShowAge())
                                .showLocation(request.getPrivacy().getShowLocation())
                                .showOccupation(request.getPrivacy().getShowOccupation())
                                .build() : null)
                .build();
    }

    private UserProfileResponse convertToResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .bio(profile.getBio())
                .interests(profile.getInterests())
                .gender(profile.getGender())
                .age(profile.getAge())
                .location(profile.getLocation())
                .occupation(profile.getOccupation())
                .education(profile.getEducation())
                .lookingFor(profile.getLookingFor())
                .photos(profile.getPhotos())
                .socialLinks(profile.getSocialLinks())
                .privacy(profile.getPrivacy())
                .build();
    }
}
