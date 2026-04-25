package com.talkingai.soulchat.repository;

import com.talkingai.soulchat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {

    Flux<ChatMessage> findByRoomIdOrderByTimestampAsc(String roomId);

    Flux<ChatMessage> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);

    Flux<ChatMessage> findBySenderIdAndReceiverIdOrderByTimestampAsc(String senderId, String receiverId);

    @Query("{ $or: [ { senderId: ?0, receiverId: ?1 }, { senderId: ?1, receiverId: ?0 } ], type: 'PRIVATE' }")
    Flux<ChatMessage> findPrivateMessages(String userId1, String userId2, Pageable pageable);

    Mono<Long> countByReceiverIdAndReadFalse(String receiverId);

    Flux<ChatMessage> findByReceiverIdAndSenderIdAndReadFalse(String receiverId, String senderId);
}
