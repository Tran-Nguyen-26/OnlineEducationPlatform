package com.oep.worker.config;

import com.oep.coremodel.constant.MessagingConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    TopicExchange oepExchange() {
        return new TopicExchange(MessagingConstants.EXCHANGE);
    }

    @Bean
    Queue notificationQueue() {
        return new Queue(MessagingConstants.QUEUE_NOTIFICATION, true);
    }

    @Bean
    Queue scoreSyncQueue() {
        return new Queue(MessagingConstants.QUEUE_SCORE_SYNC, true);
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(notificationQueue).to(oepExchange).with(MessagingConstants.RK_NOTIFICATION);
    }

    @Bean
    Binding quizPublishedBinding(Queue notificationQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(notificationQueue).to(oepExchange).with(MessagingConstants.RK_QUIZ_PUBLISHED);
    }

    @Bean
    Binding scoreSyncBinding(Queue scoreSyncQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(scoreSyncQueue).to(oepExchange).with(MessagingConstants.RK_SCORE_SYNC);
    }

    @Bean
    org.springframework.amqp.core.Queue videoProcessQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(MessagingConstants.QUEUE_VIDEO_PROCESS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", MessagingConstants.QUEUE_VIDEO_PROCESS_DLQ)
                .withArgument("x-message-ttl", 7_200_000L)
                .build();
    }

    @Bean
    org.springframework.amqp.core.Queue videoProcessDlq() {
        return org.springframework.amqp.core.QueueBuilder.durable(MessagingConstants.QUEUE_VIDEO_PROCESS_DLQ).build();
    }

    @Bean
    Binding videoProcessBinding(org.springframework.amqp.core.Queue videoProcessQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(videoProcessQueue).to(oepExchange).with(MessagingConstants.RK_VIDEO_UPLOADED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
