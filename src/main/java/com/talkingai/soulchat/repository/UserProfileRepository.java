package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.UserProfile;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserProfileRepository extends ReactiveMongoRepository<UserProfile, String> {

    Mono<UserProfile> findByUserId(String userId);

    Mono<Boolean> existsByUserId(String userId);
}
