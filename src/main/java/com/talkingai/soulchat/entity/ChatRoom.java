package com.talkingai.soulchat.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chat_rooms")
public class ChatRoom extends BaseEntity {
    
    @Indexed(unique = true)
    private String roomId;
    
    private RoomType type;
    
    private String name;
    
    private List<String> participants;
    
    private String creatorId;
    
    private Boolean active;
    
    public enum RoomType {
        PRIVATE, LOBBY
    }
}
