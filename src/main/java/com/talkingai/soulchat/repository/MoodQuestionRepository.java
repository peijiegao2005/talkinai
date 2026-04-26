package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.MoodQuestion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MoodQuestionRepository extends ReactiveMongoRepository<MoodQuestion, String> {

    Mono<MoodQuestion> findByQuestionNumber(Integer questionNumber);
}
