package com.talkingai.soulchat.service;

import com.talkingai.soulchat.entity.*;
import com.talkingai.soulchat.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoodRoomService {

    private final MoodRoomRepository roomRepository;
    private final MoodRoomUserRepository roomUserRepository;
    private final MoodChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    // WebSocket sinks for real-time messaging
    private final Map<String, Sinks.Many<MoodChatMessage>> roomSinks = new ConcurrentHashMap<>();

    private static final String MOOD_ROOM_USER_PREFIX = "mood:room:user:";
    private static final Duration USER_TTL = Duration.ofMinutes(10);

    /**
     * 初始化默认聊天室
     */
    @PostConstruct
    public void initDefaultRooms() {
        List<MoodRoom> defaultRooms = Arrays.asList(
                MoodRoom.builder()
                        .roomId("happy_station")
                        .name("开心加油站")
                        .icon("😊")
                        .description("分享快乐，传递正能量")
                        .emotions(Arrays.asList("快乐", "兴奋", "满足"))
                        .atmosphere("轻松、分享、正向")
                        .typicalScenario("今天遇到了好事，想和人分享快乐")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build(),
                MoodRoom.builder()
                        .roomId("anxiety_hole")
                        .name("焦虑树洞")
                        .icon("😰")
                        .description("匿名倾诉，互相支持")
                        .emotions(Arrays.asList("焦虑", "紧张", "不安"))
                        .atmosphere("匿名、倾听、互助")
                        .typicalScenario("工作压力大、对未来感到迷茫，想找人聊聊")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build(),
                MoodRoom.builder()
                        .roomId("peaceful_island")
                        .name("平静小岛")
                        .icon("🏝️")
                        .description("安静陪伴，舒缓心灵")
                        .emotions(Arrays.asList("平静", "放松", "专注"))
                        .atmosphere("舒缓、陪伴、共处")
                        .typicalScenario("夜晚失眠、想有人在旁边安静地陪一会儿")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build(),
                MoodRoom.builder()
                        .roomId("inspiration_spark")
                        .name("灵感火花")
                        .icon("💡")
                        .description("创意碰撞，深度交流")
                        .emotions(Arrays.asList("好奇", "兴奋", "兴趣"))
                        .atmosphere("创意、讨论、碰撞")
                        .typicalScenario("突然有个点子，或者想聊科幻、哲学等深度话题")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build(),
                MoodRoom.builder()
                        .roomId("lonely_star")
                        .name("寂寞星空")
                        .icon("🌟")
                        .description("温暖陪伴，不再孤单")
                        .emotions(Arrays.asList("孤独", "渴望连接"))
                        .atmosphere("温暖、陪伴、低压力")
                        .typicalScenario("单纯觉得一个人很孤单，想听听其他人的声音")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build(),
                MoodRoom.builder()
                        .roomId("daily_chat")
                        .name("日常茶话")
                        .icon("☕")
                        .description("轻松闲聊，社区茶馆")
                        .emotions(Arrays.asList("中性", "闲适", "无明确情绪"))
                        .atmosphere("轻松、泛话题")
                        .typicalScenario("没有强烈情绪，就想随便聊聊天")
                        .maxUsers(100)
                        .currentUsers(0)
                        .active(true)
                        .build()
        );

        Flux.fromIterable(defaultRooms)
                .flatMap(room -> roomRepository.findByRoomId(room.getRoomId())
                        .switchIfEmpty(roomRepository.save(room)))
                .collectList()
                .subscribe(
                        rooms -> log.info("Initialized {} mood rooms", rooms.size()),
                        error -> log.error("Failed to initialize mood rooms", error)
                );
    }

    /**
     * 获取所有聊天室
     */
    public Flux<MoodRoom> getAllRooms() {
        return roomRepository.findAll()
                .flatMap(this::updateRoomUserCount);
    }

    /**
     * 获取聊天室详情
     */
    public Mono<RoomDetailResponse> getRoomDetail(String roomId) {
        return roomRepository.findByRoomId(roomId)
                .flatMap(this::updateRoomUserCount)
                .flatMap(room ->
                    roomUserRepository.findByRoomId(roomId)
                            .map(user -> RoomUserDTO.builder()
                                    .userId(user.getUserId())
                                    .nickname(user.getNickname())
                                    .avatar(user.getAvatar())
                                    .currentMood(user.getCurrentMood())
                                    .allowDirectMessage(user.getAllowDirectMessage())
                                    .build())
                            .collectList()
                            .map(users -> RoomDetailResponse.builder()
                                    .roomId(room.getRoomId())
                                    .name(room.getName())
                                    .icon(room.getIcon())
                                    .description(room.getDescription())
                                    .emotions(room.getEmotions())
                                    .atmosphere(room.getAtmosphere())
                                    .typicalScenario(room.getTypicalScenario())
                                    .currentUsers(room.getCurrentUsers())
                                    .maxUsers(room.getMaxUsers())
                                    .onlineUsers(users)
                                    .build())
                );
    }

    /**
     * 加入聊天室
     */
    public Mono<Void> joinRoom(String userId, String roomId, String currentMood, Boolean allowDirectMessage) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    // 先离开之前的房间
                    return leaveCurrentRoom(userId)
                            .then(Mono.defer(() -> {
                                MoodRoomUser roomUser = MoodRoomUser.builder()
                                        .userId(userId)
                                        .roomId(roomId)
                                        .nickname(user.getNickname())
                                        .avatar(user.getAvatar())
                                        .currentMood(currentMood)
                                        .allowDirectMessage(allowDirectMessage != null ? allowDirectMessage : true)
                                        .joinedAt(System.currentTimeMillis())
                                        .lastActiveAt(System.currentTimeMillis())
                                        .build();

                                return roomUserRepository.save(roomUser);
                            }))
                            .then(redisTemplate.opsForValue()
                                    .set(MOOD_ROOM_USER_PREFIX + userId, roomId, USER_TTL))
                            .then(sendSystemMessage(roomId, user.getNickname() + " 加入了聊天室"))
                            .doOnSuccess(v -> log.info("User {} joined mood room {}", userId, roomId));
                });
    }

    /**
     * 离开聊天室
     */
    public Mono<Void> leaveRoom(String userId) {
        return leaveCurrentRoom(userId)
                .doOnSuccess(v -> log.info("User {} left mood room", userId));
    }

    /**
     * 发送消息
     */
    public Mono<MoodChatMessage> sendMessage(String userId, String roomId, String content) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    MoodChatMessage message = MoodChatMessage.builder()
                            .roomId(roomId)
                            .userId(userId)
                            .nickname(user.getNickname())
                            .avatar(user.getAvatar())
                            .content(content)
                            .messageType(MoodChatMessage.MessageType.CHAT.name())
                            .timestamp(System.currentTimeMillis())
                            .build();

                    // 保存到数据库
                    return messageRepository.save(message)
                            .flatMap(saved -> {
                                // 广播到房间
                                broadcastMessage(roomId, saved);
                                // 更新用户活跃时间
                                return updateUserActivity(userId)
                                        .thenReturn(saved);
                            });
                });
    }

    /**
     * 发送一对一邀请
     */
    public Mono<Void> sendDirectMessageInvite(String fromUserId, String toUserId, String roomId) {
        return userRepository.findById(fromUserId)
                .flatMap(fromUser ->
                    roomUserRepository.findByUserId(toUserId)
                            .flatMap(toUser -> {
                                if (!toUser.getAllowDirectMessage()) {
                                    return Mono.error(new RuntimeException("该用户不接受私信邀请"));
                                }

                                String inviteContent = String.format("%s 想和你进行一对一对话，是否接受？", fromUser.getNickname());

                                MoodChatMessage inviteMessage = MoodChatMessage.builder()
                                        .roomId(roomId)
                                        .userId(fromUserId)
                                        .nickname(fromUser.getNickname())
                                        .avatar(fromUser.getAvatar())
                                        .content(inviteContent)
                                        .messageType(MoodChatMessage.MessageType.INVITE.name())
                                        .timestamp(System.currentTimeMillis())
                                        .build();

                                // 保存并广播邀请消息
                                return messageRepository.save(inviteMessage)
                                        .doOnNext(saved -> broadcastMessage(roomId, saved))
                                        .then();
                            })
                            .switchIfEmpty(Mono.error(new RuntimeException("用户已离开房间")))
                );
    }

    /**
     * 响应一对一邀请
     */
    public Mono<Void> respondToInvite(String userId, String roomId, String fromUserId, Boolean accept) {
        return userRepository.findById(userId)
                .flatMap(user -> {
                    String responseContent = accept
                            ? String.format("%s 接受了你的邀请", user.getNickname())
                            : String.format("%s 婉拒了你的邀请", user.getNickname());

                    MoodChatMessage.MessageType messageType = accept
                            ? MoodChatMessage.MessageType.INVITE_ACCEPT
                            : MoodChatMessage.MessageType.INVITE_REJECT;

                    MoodChatMessage responseMessage = MoodChatMessage.builder()
                            .roomId(roomId)
                            .userId(userId)
                            .nickname(user.getNickname())
                            .avatar(user.getAvatar())
                            .content(responseContent)
                            .messageType(messageType.name())
                            .timestamp(System.currentTimeMillis())
                            .build();

                    return messageRepository.save(responseMessage)
                            .doOnNext(saved -> broadcastMessage(roomId, saved))
                            .then();
                });
    }

    /**
     * 订阅房间消息流（WebSocket用）
     */
    public Flux<MoodChatMessage> subscribeToRoom(String roomId) {
        return getOrCreateSink(roomId).asFlux();
    }

    /**
     * 获取房间历史消息（最近50条，只返回用户加入房间后的消息）
     */
    public Flux<MoodChatMessage> getRoomHistory(String roomId, String userId) {
        return roomUserRepository.findByUserId(userId)
                .filter(user -> roomId.equals(user.getRoomId()))
                .flatMapMany(user -> {
                    Long joinedAt = user.getJoinedAt();
                    return messageRepository.findByRoomIdAndTimestampGreaterThanEqualOrderByTimestampDesc(roomId, joinedAt)
                            .take(50);
                })
                .switchIfEmpty(Flux.empty());
    }

    // ==================== Private Methods ====================

    private Mono<Void> leaveCurrentRoom(String userId) {
        return roomUserRepository.findByUserId(userId)
                .flatMap(roomUser ->
                    roomRepository.findByRoomId(roomUser.getRoomId())
                            .flatMap(room -> {
                                String roomId = room.getRoomId();
                                return sendSystemMessage(roomId, roomUser.getNickname() + " 离开了聊天室")
                                        .then(roomUserRepository.deleteByUserId(userId))
                                        .then(redisTemplate.delete(MOOD_ROOM_USER_PREFIX + userId))
                                        .then(updateRoomUserCount(room));
                            })
                            .then()
                )
                .switchIfEmpty(Mono.empty());
    }

    private Mono<Void> sendSystemMessage(String roomId, String content) {
        MoodChatMessage message = MoodChatMessage.builder()
                .roomId(roomId)
                .userId("system")
                .nickname("系统")
                .content(content)
                .messageType(MoodChatMessage.MessageType.SYSTEM.name())
                .timestamp(System.currentTimeMillis())
                .build();

        return messageRepository.save(message)
                .doOnNext(saved -> broadcastMessage(roomId, saved))
                .then();
    }

    private void broadcastMessage(String roomId, MoodChatMessage message) {
        Sinks.Many<MoodChatMessage> sink = roomSinks.get(roomId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }

    private Sinks.Many<MoodChatMessage> getOrCreateSink(String roomId) {
        return roomSinks.computeIfAbsent(roomId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
    }

    private Mono<MoodRoom> updateRoomUserCount(MoodRoom room) {
        return roomUserRepository.findByRoomId(room.getRoomId())
                .count()
                .map(count -> {
                    room.setCurrentUsers(count.intValue());
                    return room;
                });
    }

    private Mono<Void> updateUserActivity(String userId) {
        return roomUserRepository.findByUserId(userId)
                .flatMap(roomUser -> {
                    roomUser.setLastActiveAt(System.currentTimeMillis());
                    return roomUserRepository.save(roomUser);
                })
                .then(redisTemplate.opsForValue()
                        .set(MOOD_ROOM_USER_PREFIX + userId, roomUserRepository.findByUserId(userId).map(MoodRoomUser::getRoomId).block(), USER_TTL))
                .then();
    }

    // ==================== DTOs ====================

    @Data
    @Builder
    public static class RoomDetailResponse {
        private String roomId;
        private String name;
        private String icon;
        private String description;
        private List<String> emotions;
        private String atmosphere;
        private String typicalScenario;
        private Integer currentUsers;
        private Integer maxUsers;
        private List<RoomUserDTO> onlineUsers;
    }

    @Data
    @Builder
    public static class RoomUserDTO {
        private String userId;
        private String nickname;
        private String avatar;
        private String currentMood;
        private Boolean allowDirectMessage;
    }

    @Data
    @Builder
    public static class ChatMessageDTO {
        private String id;
        private String userId;
        private String nickname;
        private String avatar;
        private String content;
        private String messageType;
        private Long timestamp;
    }
}
