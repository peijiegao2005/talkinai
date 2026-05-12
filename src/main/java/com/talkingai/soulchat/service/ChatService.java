package com.talkingai.soulchat.service;

import com.talkingai.soulchat.dto.ChatRoomDTO;
import com.talkingai.soulchat.entity.ChatMessage;
import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.entity.User;
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

    /**
     * 获取用户的聊天室列表，包含对方用户信息和最后一条消息
     */
    public Flux<ChatRoomDTO> getUserRoomsWithDetails(String userId) {
        return chatRoomRepository.findByParticipantsContainingAndActiveTrue(userId)
                .flatMap(room -> {
                    // 找到对方用户ID
                    String partnerId = room.getParticipants().stream()
                            .filter(id -> !id.equals(userId))
                            .findFirst()
                            .orElse(null);

                    if (partnerId == null) {
                        return Mono.just(convertToDTO(room, null, null));
                    }

                    // 获取对方用户信息
                    return userRepository.findById(partnerId)
                            .map(partner -> convertToDTO(room, partnerId, partner))
                            .defaultIfEmpty(convertToDTO(room, partnerId, null));
                });
    }

    private ChatRoomDTO convertToDTO(ChatRoom room, String partnerId, User partner) {
        return ChatRoomDTO.builder()
                .roomId(room.getRoomId())
                .type(room.getType().name())
                .name(room.getName())
                .participants(room.getParticipants())
                .active(room.getActive() != null ? room.getActive() : true)
                .partnerId(partnerId)
                .partnerNickname(partner != null ? partner.getNickname() : "未知用户")
                .partnerAvatar(partner != null ? partner.getAvatar() : null)
                .build();
    }

    /**
     * 删除聊天室（软删除，标记为inactive）
     */
    public Mono<Void> deleteRoom(String userId, String roomId) {
        return chatRoomRepository.findByRoomId(roomId)
                .flatMap(room -> {
                    // 验证用户是参与者
                    if (!room.getParticipants().contains(userId)) {
                        return Mono.error(new RuntimeException("无权删除此聊天室"));
                    }
                    
                    // 从参与者列表中移除
                    room.getParticipants().remove(userId);
                    
                    // 如果没有参与者了，标记为inactive
                    if (room.getParticipants().isEmpty()) {
                        room.setActive(false);
                    }
                    
                    return chatRoomRepository.save(room).then();
                });
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
