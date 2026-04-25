package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.UserProfile;
import com.talkingai.soulchat.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public Mono<UserProfile> getProfile(String userId) {
        return userProfileRepository.findByUserId(userId)
                .switchIfEmpty(createDefaultProfile(userId));
    }

    public Mono<UserProfile> createOrUpdateProfile(String userId, UserProfile profile) {
        return userProfileRepository.findByUserId(userId)
                .flatMap(existing -> {
                    existing.setBio(profile.getBio());
                    existing.setInterests(profile.getInterests());
                    existing.setGender(profile.getGender());
                    existing.setAge(profile.getAge());
                    existing.setLocation(profile.getLocation());
                    existing.setOccupation(profile.getOccupation());
                    existing.setEducation(profile.getEducation());
                    existing.setLookingFor(profile.getLookingFor());
                    existing.setPhotos(profile.getPhotos());
                    existing.setSocialLinks(profile.getSocialLinks());
                    existing.setPrivacy(profile.getPrivacy());
                    return userProfileRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    profile.setUserId(userId);
                    return userProfileRepository.save(profile);
                }))
                .doOnSuccess(p -> log.info("用户资料已更新: {}", userId));
    }

    public Mono<UserProfile> updateBio(String userId, String bio) {
        return userProfileRepository.findByUserId(userId)
                .flatMap(profile -> {
                    profile.setBio(bio);
                    return userProfileRepository.save(profile);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserProfile profile = UserProfile.builder()
                            .userId(userId)
                            .bio(bio)
                            .build();
                    return userProfileRepository.save(profile);
                }));
    }

    public Mono<UserProfile> addInterest(String userId, String interest) {
        return userProfileRepository.findByUserId(userId)
                .flatMap(profile -> {
                    if (profile.getInterests() == null) {
                        profile.setInterests(new java.util.ArrayList<>());
                    }
                    if (!profile.getInterests().contains(interest)) {
                        profile.getInterests().add(interest);
                    }
                    return userProfileRepository.save(profile);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    UserProfile profile = UserProfile.builder()
                            .userId(userId)
                            .interests(java.util.List.of(interest))
                            .build();
                    return userProfileRepository.save(profile);
                }));
    }

    private Mono<UserProfile> createDefaultProfile(String userId) {
        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .bio("这个人很懒，什么都没写~")
                .interests(new java.util.ArrayList<>())
                .privacy(UserProfile.PrivacySettings.builder()
                        .showAge(true)
                        .showLocation(false)
                        .showOccupation(false)
                        .build())
                .build();
        return userProfileRepository.save(profile);
    }
}
