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
    Queue forgotPasswordEmailQueue() {
        return new Queue(MessagingConstants.QUEUE_FORGOT_PASSWORD_EMAIL, true);
    }

    @Bean
    Queue instructorApplicationQueue() {
        return new Queue(MessagingConstants.QUEUE_INSTRUCTOR_APPLICATION, true);
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
    Binding forgotPasswordEmailBinding(Queue forgotPasswordEmailQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(forgotPasswordEmailQueue).to(oepExchange).with(MessagingConstants.RK_FORGOT_PASSWORD_EMAIL);
    }

    @Bean
    Binding instructorApprovedBinding(Queue instructorApplicationQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(instructorApplicationQueue).to(oepExchange).with(MessagingConstants.RK_INSTRUCTOR_APPROVED);
    }

    @Bean
    Binding instructorRejectedBinding(Queue instructorApplicationQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(instructorApplicationQueue).to(oepExchange).with(MessagingConstants.RK_INSTRUCTOR_REJECTED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
