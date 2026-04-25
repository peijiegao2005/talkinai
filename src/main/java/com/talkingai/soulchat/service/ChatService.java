package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.ChatMessage;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.repository.ChatMessageRepository;
import com.talkingai.soulchat.repository.ChatRoomRepository;
import com.talkingai.soulchat.repository.UserRepository;
import com.talkingai.soulchat.security.content.ContentModerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final ContentModerationService contentModerationService;

    public Mono<ChatMessage> saveMessage(String senderId, String receiverId, String content) {
        return contentModerationService.moderateText(content)
                .flatMap(result -> {
                    String finalContent = result.isApproved() ? content : result.getFilteredContent();

                    ChatMessage message = ChatMessage.builder()
                            .senderId(senderId)
                            .receiverId(receiverId)
                            .content(finalContent)
                            .type(ChatMessage.MessageType.PRIVATE)
                            .timestamp(Instant.now().toEpochMilli())
                            .read(false)
                            .build();

                    return chatMessageRepository.save(message);
                })
                .doOnSuccess(m -> log.debug("消息已保存: from={} to={}", senderId, receiverId));
    }

    public Mono<ChatMessage> saveRoomMessage(String senderId, String roomId, String content) {
        return contentModerationService.moderateText(content)
                .flatMap(result -> {
                    String finalContent = result.isApproved() ? content : result.getFilteredContent();

                    ChatMessage message = ChatMessage.builder()
                            .senderId(senderId)
                            .roomId(roomId)
                            .content(finalContent)
                            .type(ChatMessage.MessageType.GROUP)
                            .timestamp(Instant.now().toEpochMilli())
                            .read(false)
                            .build();

                    return chatMessageRepository.save(message);
                });
    }

    public Flux<ChatMessage> getPrivateMessages(String userId1, String userId2, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return chatMessageRepository.findPrivateMessages(userId1, userId2, pageable);
    }

    public Flux<ChatMessage> getRoomMessages(String roomId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
    }

    public Mono<Long> getUnreadCount(String userId) {
        return chatMessageRepository.countByReceiverIdAndReadFalse(userId);
    }

    public Mono<Void> markAsRead(String messageId) {
        return chatMessageRepository.findById(messageId)
                .flatMap(message -> {
                    message.setRead(true);
                    return chatMessageRepository.save(message);
                })
                .then();
    }

    public Mono<Void> markAllAsRead(String userId, String senderId) {
        return chatMessageRepository.findByReceiverIdAndSenderIdAndReadFalse(userId, senderId)
                .flatMap(message -> {
                    message.setRead(true);
                    return chatMessageRepository.save(message);
                })
                .then();
    }

    public Mono<ChatRoom> createPrivateRoom(String userId1, String userId2) {
        String roomId = generatePrivateRoomId(userId1, userId2);

        return chatRoomRepository.findByRoomId(roomId)
                .switchIfEmpty(Mono.defer(() -> {
                    List<String> participants = new ArrayList<>();
                    participants.add(userId1);
                    participants.add(userId2);

                    ChatRoom room = ChatRoom.builder()
                            .roomId(roomId)
                            .type(ChatRoom.RoomType.PRIVATE)
                            .name("私聊")
                            .participants(participants)
                            .creatorId(userId1)
                            .active(true)
                            .build();

                    return chatRoomRepository.save(room);
                }));
    }

    public Mono<ChatRoom> getOrCreateMatchRoom(String userId1, String userId2) {
        return createPrivateRoom(userId1, userId2)
                .flatMap(room ->
                    userRepository.findById(userId2)
                            .map(matchUser -> {
                                room.setName(matchUser.getNickname() != null ? matchUser.getNickname() : "灵魂伴侣");
                                return room;
                            })
                            .defaultIfEmpty(room)
                );
    }

    public Mono<Void> joinRoom(String userId, String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .flatMap(room -> {
                    if (!room.getParticipants().contains(userId)) {
                        room.getParticipants().add(userId);
                        return chatRoomRepository.save(room).then();
                    }
                    return Mono.empty();
                });
    }

    public Mono<Void> leaveRoom(String userId, String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .flatMap(room -> {
                    room.getParticipants().remove(userId);
                    if (room.getParticipants().isEmpty()) {
                        room.setActive(false);
                    }
                    return chatRoomRepository.save(room).then();
                });
    }

    public Flux<ChatRoom> getUserRooms(String userId) {
        return chatRoomRepository.findByParticipantsContainingAndActiveTrue(userId);
    }

    private String generatePrivateRoomId(String userId1, String userId2) {
        // 确保roomId唯一且一致（无论谁创建）
        if (userId1.compareTo(userId2) < 0) {
            return "private:" + userId1 + ":" + userId2;
        } else {
            return "private:" + userId2 + ":" + userId1;
        }
    }
}
