package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.ChatMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
    
    Flux<ChatMessage> findByRoomIdOrderByTimestampAsc(String roomId);
    
    Flux<ChatMessage> findBySenderIdAndReceiverIdOrderByTimestampAsc(String senderId, String receiverId);
}
