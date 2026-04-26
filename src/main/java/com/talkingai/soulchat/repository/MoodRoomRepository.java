package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.MoodRoom;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface MoodRoomRepository extends ReactiveMongoRepository<MoodRoom, String> {

    Mono<MoodRoom> findByRoomId(String roomId);

    Mono<MoodRoom> findByName(String name);
}
