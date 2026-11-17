package com.oep.worker.config;

import com.oep.coremodel.constant.MessagingConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    /** Messages that fail after the listener's retries are dead-lettered here instead of being requeued forever. */
    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(MessagingConstants.DLX);
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(MessagingConstants.QUEUE_NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding notificationDeadLetterBinding(Queue notificationDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(deadLetterExchange).with(MessagingConstants.DLQ_ROUTING_KEY);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(MessagingConstants.QUEUE_NOTIFICATION)
                .deadLetterExchange(MessagingConstants.DLX)
                .deadLetterRoutingKey(MessagingConstants.DLQ_ROUTING_KEY)
                .build();
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
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
