package com.talkingai.soulchat.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chat_messages")
public class ChatMessage extends BaseEntity {
    
    @Indexed
    private String roomId;
    
    @Indexed
    private String senderId;
    
    private String receiverId;
    
    private String content;
    
    private MessageType type;
    
    private Long timestamp;
    
    private Boolean read;
    
    public enum MessageType {
        PRIVATE, GROUP, SYSTEM
    }
}
