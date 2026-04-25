package com.talkingai.soulchat.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "users")
public class User extends BaseEntity {
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String password;
    
    private String nickname;
    
    private String avatar;
    
    private List<Double> personalityVector;
    
    private Boolean hasAssessment;
    
    private Boolean online;
    
    private Long lastActiveAt;
}
