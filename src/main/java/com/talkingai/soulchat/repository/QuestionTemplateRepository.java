package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.QuestionTemplate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface QuestionTemplateRepository extends ReactiveMongoRepository<QuestionTemplate, String> {
    
    Mono<QuestionTemplate> findByQuestionNumber(Integer questionNumber);
    
    Flux<QuestionTemplate> findAllByOrderByQuestionNumberAsc();
}
