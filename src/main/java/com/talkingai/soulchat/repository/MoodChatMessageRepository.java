package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.MoodChatMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface MoodChatMessageRepository extends ReactiveMongoRepository<MoodChatMessage, String> {

    Flux<MoodChatMessage> findByRoomIdOrderByTimestampDesc(String roomId);

    Flux<MoodChatMessage> findByRoomIdAndTimestampGreaterThanOrderByTimestampAsc(String roomId, Long timestamp);

    Flux<MoodChatMessage> findByRoomIdAndTimestampGreaterThanEqualOrderByTimestampDesc(String roomId, Long timestamp);
}
