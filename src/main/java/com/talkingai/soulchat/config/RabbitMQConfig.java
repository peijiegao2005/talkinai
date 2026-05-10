package com.talkingai.soulchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.rabbitmq.*;

/**
 * RabbitMQ异步解耦配置
 * 用于聊天消息异步持久化、匹配事件通知
 */
@Slf4j
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String CHAT_MESSAGE_QUEUE = "soulchat.chat.message";
    public static final String CHAT_MESSAGE_EXCHANGE = "soulchat.chat.exchange";
    public static final String MATCH_EVENT_QUEUE = "soulchat.match.event";
    public static final String MATCH_EVENT_EXCHANGE = "soulchat.match.exchange";

    // ==================== 队列与交换机 ====================

    @Bean
    public Queue chatMessageQueue() {
        return QueueBuilder.durable(CHAT_MESSAGE_QUEUE)
                .withArgument("x-max-length", 10000)
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public TopicExchange chatMessageExchange() {
        return new TopicExchange(CHAT_MESSAGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding chatMessageBinding() {
        return BindingBuilder.bind(chatMessageQueue())
                .to(chatMessageExchange())
                .with("chat.message.#");
    }

    @Bean
    public Queue matchEventQueue() {
        return QueueBuilder.durable(MATCH_EVENT_QUEUE)
                .withArgument("x-max-length", 5000)
                .build();
    }

    @Bean
    public TopicExchange matchEventExchange() {
        return new TopicExchange(MATCH_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Binding matchEventBinding() {
        return BindingBuilder.bind(matchEventQueue())
                .to(matchEventExchange())
                .with("match.event.#");
    }

    // ==================== Reactor RabbitMQ ====================

    @Bean
    public SenderOptions senderOptions(org.springframework.amqp.rabbit.connection.CachingConnectionFactory cachingConnectionFactory) {
        com.rabbitmq.client.ConnectionFactory connectionFactory = cachingConnectionFactory.getRabbitConnectionFactory();
        return new SenderOptions()
                .connectionFactory(connectionFactory)
                .resourceManagementScheduler(
                        reactor.core.scheduler.Schedulers.boundedElastic()
                );
    }

    @Bean
    public Sender sender(SenderOptions senderOptions) {
        return RabbitFlux.createSender(senderOptions);
    }

    @Bean
    public ReceiverOptions receiverOptions(org.springframework.amqp.rabbit.connection.CachingConnectionFactory cachingConnectionFactory) {
        com.rabbitmq.client.ConnectionFactory connectionFactory = cachingConnectionFactory.getRabbitConnectionFactory();
        return new ReceiverOptions()
                .connectionFactory(connectionFactory);
    }

    @Bean
    public Receiver receiver(ReceiverOptions receiverOptions) {
        return RabbitFlux.createReceiver(receiverOptions);
    }
}
