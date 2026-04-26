package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.MoodAssessment;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MoodAssessmentRepository extends ReactiveMongoRepository<MoodAssessment, String> {

    Flux<MoodAssessment> findByUserIdOrderByCreatedAtDesc(String userId);

    Mono<MoodAssessment> findBySessionId(String sessionId);

    Mono<MoodAssessment> findTopByUserIdOrderByCreatedAtDesc(String userId);
}
