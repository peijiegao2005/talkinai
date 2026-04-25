package com.talkingai.soulchat.security.content;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SensitiveWordFilter {

    private final Map<Character, Map> sensitiveWordMap = new HashMap<>();
    private static final char END_FLAG = '\0';
    private static final String REPLACEMENT = "***";

    @PostConstruct
    public void init() {
        loadDefaultSensitiveWords();
        log.info("敏感词过滤器初始化完成，共加载 {} 个敏感词", sensitiveWordMap.size());
    }

    private void loadDefaultSensitiveWords() {
        // 默认敏感词库 - 实际项目中应从数据库或配置文件加载
        List<String> words = Arrays.asList(
                "脏话", "骂人", "暴力", "色情", "赌博", "毒品", "诈骗",
                "fuck", "shit", "damn", "bitch", "asshole"
        );
        words.forEach(this::addWord);
    }

    public void addWord(String word) {
        if (word == null || word.isEmpty()) return;

        Map<Character, Map> currentMap = sensitiveWordMap;
        for (char c : word.toCharArray()) {
            currentMap = currentMap.computeIfAbsent(c, k -> new HashMap<>());
        }
        currentMap.put(END_FLAG, new HashMap<>());
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) return false;

        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                return true;
            }
        }
        return false;
    }

    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder(text);
        int index = 0;

        while (index < result.length()) {
            int length = checkSensitiveWord(result.toString(), index);
            if (length > 0) {
                result.replace(index, index + length, REPLACEMENT);
                index += REPLACEMENT.length();
            } else {
                index++;
            }
        }

        return result.toString();
    }

    public List<String> findSensitiveWords(String text) {
        List<String> words = new ArrayList<>();
        if (text == null || text.isEmpty()) return words;

        for (int i = 0; i < text.length(); i++) {
            int length = checkSensitiveWord(text, i);
            if (length > 0) {
                words.add(text.substring(i, i + length));
                i += length - 1;
            }
        }

        return words;
    }

    private int checkSensitiveWord(String text, int beginIndex) {
        Map<Character, Map> currentMap = sensitiveWordMap;
        int matchLength = 0;
        int maxLength = 0;

        for (int i = beginIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            currentMap = currentMap.get(c);

            if (currentMap == null) {
                break;
            }

            matchLength++;
            if (currentMap.containsKey(END_FLAG)) {
                maxLength = matchLength;
            }
        }

        return maxLength;
    }
}
