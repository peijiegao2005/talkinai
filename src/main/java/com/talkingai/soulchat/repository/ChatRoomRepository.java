package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.ChatRoom;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ChatRoomRepository extends ReactiveMongoRepository<ChatRoom, String> {
    
    Mono<ChatRoom> findByRoomId(String roomId);
    
    Flux<ChatRoom> findByParticipantsContaining(String userId);
    
    Flux<ChatRoom> findByType(ChatRoom.RoomType type);
}
