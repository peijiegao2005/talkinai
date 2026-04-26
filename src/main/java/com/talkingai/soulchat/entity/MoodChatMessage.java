package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 心情聊天室消息
 * 后台持久化存储，前端不永久保存
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "mood_chat_messages")
public class MoodChatMessage extends BaseEntity {

    @Indexed
    private String roomId;

    @Indexed
    private String userId;

    private String nickname;

    private String avatar;

    private String content;

    private String messageType;

    private Long timestamp;

    public enum MessageType {
        CHAT,       // 普通聊天
        JOIN,       // 加入房间
        LEAVE,      // 离开房间
        INVITE,     // 一对一邀请
        INVITE_ACCEPT,  // 接受邀请
        INVITE_REJECT,  // 拒绝邀请
        SYSTEM      // 系统消息
    }
}
