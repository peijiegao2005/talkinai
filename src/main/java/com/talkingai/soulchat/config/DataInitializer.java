package com.talkingai.soulchat.config;

import com.talkingai.soulchat.entity.ChatRoom;
import com.talkingai.soulchat.entity.MoodQuestion;
import com.talkingai.soulchat.entity.QuestionTemplate;
import com.talkingai.soulchat.repository.ChatRoomRepository;
import com.talkingai.soulchat.repository.MoodQuestionRepository;
import com.talkingai.soulchat.repository.QuestionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final QuestionTemplateRepository questionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final MoodQuestionRepository moodQuestionRepository;

    @Override
    public void run(String... args) {
        initQuestions()
                .then(initLobbyRoom())
                .then(initMoodQuestions())
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

    private Mono<Void> initMoodQuestions() {
        List<MoodQuestion> questions = Arrays.asList(
                // 问题1: 当前情绪状态
                MoodQuestion.builder()
                        .questionNumber(1)
                        .content("此刻，最能描述你心情的词是？")
                        .options(Arrays.asList(
                                MoodQuestion.MoodOption.builder().label("A").text("开心、愉悦").score(5).build(),
                                MoodQuestion.MoodOption.builder().label("B").text("平静、放松").score(4).build(),
                                MoodQuestion.MoodOption.builder().label("C").text("焦虑、紧张").score(3).build(),
                                MoodQuestion.MoodOption.builder().label("D").text("孤独、寂寞").score(2).build(),
                                MoodQuestion.MoodOption.builder().label("E").text("疲惫、无感").score(1).build()
                        ))
                        .build(),

                // 问题2: 能量水平
                MoodQuestion.builder()
                        .questionNumber(2)
                        .content("你现在的精力状态如何？")
                        .options(Arrays.asList(
                                MoodQuestion.MoodOption.builder().label("A").text("充满活力，干劲十足").score(5).build(),
                                MoodQuestion.MoodOption.builder().label("B").text("精力充沛，状态不错").score(4).build(),
                                MoodQuestion.MoodOption.builder().label("C").text("平静稳定，节奏舒缓").score(3).build(),
                                MoodQuestion.MoodOption.builder().label("D").text("有些疲惫，需要休息").score(2).build(),
                                MoodQuestion.MoodOption.builder().label("E").text("精疲力尽，提不起劲").score(1).build()
                        ))
                        .build(),

                // 问题3: 社交意愿
                MoodQuestion.builder()
                        .questionNumber(3)
                        .content("你现在想和人交流吗？")
                        .options(Arrays.asList(
                                MoodQuestion.MoodOption.builder().label("A").text("非常想，想找人聊天分享").score(5).build(),
                                MoodQuestion.MoodOption.builder().label("B").text("愿意，可以轻松交流").score(4).build(),
                                MoodQuestion.MoodOption.builder().label("C").text("看情况，视话题而定").score(3).build(),
                                MoodQuestion.MoodOption.builder().label("D").text("不太想，只想听听别人").score(2).build(),
                                MoodQuestion.MoodOption.builder().label("E").text("不想，希望一个人静静").score(1).build()
                        ))
                        .build(),

                // 问题4: 思维状态
                MoodQuestion.builder()
                        .questionNumber(4)
                        .content("你的大脑现在是什么状态？")
                        .options(Arrays.asList(
                                MoodQuestion.MoodOption.builder().label("A").text("思维活跃，有很多想法").score(5).build(),
                                MoodQuestion.MoodOption.builder().label("B").text("专注某件事，很投入").score(4).build(),
                                MoodQuestion.MoodOption.builder().label("C").text("思绪平静，没有杂念").score(3).build(),
                                MoodQuestion.MoodOption.builder().label("D").text("有些担忧，在想事情").score(2).build(),
                                MoodQuestion.MoodOption.builder().label("E").text("头脑空白，什么都不想").score(1).build()
                        ))
                        .build(),

                // 问题5: 期望的氛围
                MoodQuestion.builder()
                        .questionNumber(5)
                        .content("你希望进入一个什么样的聊天氛围？")
                        .options(Arrays.asList(
                                MoodQuestion.MoodOption.builder().label("A").text("热闹欢快，充满活力").score(5).build(),
                                MoodQuestion.MoodOption.builder().label("B").text("温暖陪伴，有人倾听").score(4).build(),
                                MoodQuestion.MoodOption.builder().label("C").text("安静舒缓，轻松自在").score(3).build(),
                                MoodQuestion.MoodOption.builder().label("D").text("深度交流，思想碰撞").score(2).build(),
                                MoodQuestion.MoodOption.builder().label("E").text("随意轻松，没有压力").score(1).build()
                        ))
                        .build()
        );

        return Flux.fromIterable(questions)
                .flatMap(q -> moodQuestionRepository.findByQuestionNumber(q.getQuestionNumber())
                        .switchIfEmpty(moodQuestionRepository.save(q)))
                .collectList()
                .doOnNext(saved -> log.info("Initialized {} mood questions", saved.size()))
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
