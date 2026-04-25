package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository extends ReactiveMongoRepository<User, String> {
    
    Mono<User> findByUsername(String username);
    
    Mono<User> findByEmail(String email);
    
    Mono<Boolean> existsByUsername(String username);
    
    Mono<Boolean> existsByEmail(String email);
    
    Flux<User> findByOnlineTrue();
    
    Flux<User> findByHasAssessmentTrueAndOnlineTrue();
}
