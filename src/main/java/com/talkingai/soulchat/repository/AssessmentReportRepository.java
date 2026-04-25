package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.AssessmentReport;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AssessmentReportRepository extends ReactiveMongoRepository<AssessmentReport, String> {
    
    Mono<AssessmentReport> findByUserId(String userId);
    
    Mono<Boolean> existsByUserId(String userId);
}
