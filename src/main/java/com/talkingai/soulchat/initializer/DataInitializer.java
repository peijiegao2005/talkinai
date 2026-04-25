package com.talkingai.soulchat.initializer;

import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.entity.QuestionTemplate;
import com.talkingai.soulchat.repository.ChatRoomRepository;
import com.talkingai.soulchat.repository.QuestionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final QuestionTemplateRepository questionRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Override
    public void run(String... args) {
        initQuestions()
                .then(initLobbyRoom())
                .subscribe(
                        null,
                        error -> log.error("Data initialization failed", error),
                        () -> log.info("Data initialization completed")
                );
    }

    private Mono<Void> initQuestions() {
        return questionRepository.count()
                .flatMap(count -> {
                    if (count > 0) {
                        log.info("Questions already initialized, skipping...");
                        return Mono.empty();
                    }
                    
                    List<QuestionTemplate> questions = Arrays.asList(
                            createQuestion(1, "在社交场合中，你通常：", "extraversion", 
                                    Arrays.asList(
                                            createOption("A", "主动与陌生人交谈，享受社交", 5),
                                            createOption("B", "与熟悉的朋友交流", 3),
                                            createOption("C", "等待别人来找我", 2),
                                            createOption("D", "倾向于独处或离开", 1)
                                    )),
                            createQuestion(2, "面对突发变化，你的反应是：", "openness",
                                    Arrays.asList(
                                            createOption("A", "兴奋，喜欢新的挑战", 5),
                                            createOption("B", "适应较快，保持开放", 4),
                                            createOption("C", "需要一些时间适应", 2),
                                            createOption("D", "感到不安，喜欢稳定", 1)
                                    )),
                            createQuestion(3, "当朋友遇到困难时，你会：", "agreeableness",
                                    Arrays.asList(
                                            createOption("A", "主动提供情感支持和帮助", 5),
                                            createOption("B", "倾听并给予建议", 4),
                                            createOption("C", "视情况而定", 3),
                                            createOption("D", "让他们自己解决", 1)
                                    )),
                            createQuestion(4, "工作或学习时，你更倾向于：", "conscientiousness",
                                    Arrays.asList(
                                            createOption("A", "提前规划，严格执行", 5),
                                            createOption("B", "有计划但保持灵活", 4),
                                            createOption("C", "随性而为，最后冲刺", 2),
                                            createOption("D", "拖延到最后一刻", 1)
                                    )),
                            createQuestion(5, "遇到压力时，你通常会：", "emotional_stability",
                                    Arrays.asList(
                                            createOption("A", "保持冷静，理性分析", 5),
                                            createOption("B", "短暂焦虑后恢复", 3),
                                            createOption("C", "情绪波动较大", 2),
                                            createOption("D", "容易崩溃或逃避", 1)
                                    )),
                            createQuestion(6, "周末你更喜欢：", "extraversion",
                                    Arrays.asList(
                                            createOption("A", "参加聚会或社交活动", 5),
                                            createOption("B", "与少数好友小聚", 3),
                                            createOption("C", "在家看书或看电影", 2),
                                            createOption("D", "独自外出或运动", 1)
                                    )),
                            createQuestion(7, "对新奇事物的态度：", "openness",
                                    Arrays.asList(
                                            createOption("A", "非常好奇，总想尝试", 5),
                                            createOption("B", "感兴趣，会了解后决定", 4),
                                            createOption("C", "持观望态度", 2),
                                            createOption("D", "不太感兴趣", 1)
                                    )),
                            createQuestion(8, "团队合作中，你通常：", "agreeableness",
                                    Arrays.asList(
                                            createOption("A", "主动协调，照顾他人感受", 5),
                                            createOption("B", "配合团队，表达意见", 4),
                                            createOption("C", "专注于自己的任务", 3),
                                            createOption("D", "坚持己见，争取主导", 1)
                                    )),
                            createQuestion(9, "你的房间或桌面通常是：", "conscientiousness",
                                    Arrays.asList(
                                            createOption("A", "井井有条，物品归位", 5),
                                            createOption("B", "大致整洁", 3),
                                            createOption("C", "有些凌乱但能找到东西", 2),
                                            createOption("D", "非常混乱", 1)
                                    )),
                            createQuestion(10, "收到批评时，你的反应：", "emotional_stability",
                                    Arrays.asList(
                                            createOption("A", "客观分析，有则改之", 5),
                                            createOption("B", "有些不舒服但能接受", 3),
                                            createOption("C", "情绪低落一段时间", 2),
                                            createOption("D", "强烈抵触或自我怀疑", 1)
                                    ))
                    );
                    
                    return questionRepository.saveAll(questions)
                            .doOnComplete(() -> log.info("Initialized {} questions", questions.size()))
                            .then();
                });
    }

    private Mono<Void> initLobbyRoom() {
        return chatRoomRepository.findByRoomId("lobby")
                .switchIfEmpty(Mono.defer(() -> {
                    ChatRoom lobby = new ChatRoom();
                    lobby.setRoomId("lobby");
                    lobby.setType(ChatRoom.RoomType.LOBBY);
                    lobby.setName("广场");
                    lobby.setActive(true);
                    return chatRoomRepository.save(lobby);
                }))
                .doOnNext(room -> log.info("Lobby room ready: {}", room.getRoomId()))
                .then();
    }

    private QuestionTemplate createQuestion(int number, String content, String dimension, 
                                            List<QuestionTemplate.Option> options) {
        QuestionTemplate q = new QuestionTemplate();
        q.setQuestionNumber(number);
        q.setContent(content);
        q.setDimension(dimension);
        q.setOptions(options);
        return q;
    }

    private QuestionTemplate.Option createOption(String label, String text, int score) {
        QuestionTemplate.Option option = new QuestionTemplate.Option();
        option.setLabel(label);
        option.setText(text);
        option.setScore(score);
        return option;
    }
}
