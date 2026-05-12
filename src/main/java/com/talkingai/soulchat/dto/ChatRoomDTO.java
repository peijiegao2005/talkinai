package com.talkingai.soulchat.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatRoomDTO {
    private String roomId;
    private String type;
    private String name;
    private List<String> participants;
    private boolean active;
    
    // 对方用户信息
    private String partnerId;
    private String partnerNickname;
    private String partnerAvatar;
    
    // 最后一条消息
    private String lastMessage;
    private Long lastMessageTime;
    private int unreadCount;
}
