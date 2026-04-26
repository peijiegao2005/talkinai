package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.MoodRoomUser;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface MoodRoomUserRepository extends ReactiveMongoRepository<MoodRoomUser, String> {

    Mono<MoodRoomUser> findByUserId(String userId);

    Flux<MoodRoomUser> findByRoomId(String roomId);

    Mono<Void> deleteByUserId(String userId);
}
