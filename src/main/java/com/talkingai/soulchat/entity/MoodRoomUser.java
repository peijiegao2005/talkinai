package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 心情聊天室在线用户
 * 记录用户当前所在的聊天室
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "mood_room_users")
public class MoodRoomUser extends BaseEntity {

    @Indexed(unique = true)
    private String userId;

    @Indexed
    private String roomId;

    private String nickname;

    private String avatar;

    private String currentMood;

    private Boolean allowDirectMessage;

    private Long joinedAt;

    private Long lastActiveAt;
}
