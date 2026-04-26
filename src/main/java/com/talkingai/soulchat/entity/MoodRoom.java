package com.talkingai.soulchat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 心情聊天室
 * 对应不同情绪状态的聊天房间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "mood_rooms")
public class MoodRoom extends BaseEntity {

    @Indexed(unique = true)
    private String roomId;

    private String name;

    private String icon;

    private String description;

    private List<String> emotions;

    private String atmosphere;

    private String typicalScenario;

    private Integer maxUsers;

    private Integer currentUsers;

    private Boolean active;

    public enum RoomType {
        HAPPY_STATION("happy_station", "开心加油站", "😊"),
        ANXIETY_HOLE("anxiety_hole", "焦虑树洞", "😰"),
        PEACEFUL_ISLAND("peaceful_island", "平静小岛", "🏝️"),
        INSPIRATION_SPARK("inspiration_spark", "灵感火花", "💡"),
        LONELY_STAR("lonely_star", "寂寞星空", "🌟"),
        DAILY_CHAT("daily_chat", "日常茶话", "☕");

        private final String code;
        private final String name;
        private final String icon;

        RoomType(String code, String name, String icon) {
            this.code = code;
            this.name = name;
            this.icon = icon;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getIcon() { return icon; }
    }
}
