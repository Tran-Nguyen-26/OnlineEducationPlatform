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

    // ---- video transcoding: one job at a time, failures are dead-lettered ----------------------------------------------
    @Bean
    Queue videoQueue() {
        return QueueBuilder.durable(MessagingConstants.QUEUE_VIDEO)
                .deadLetterExchange(MessagingConstants.DLX).deadLetterRoutingKey(MessagingConstants.DLQ_VIDEO_KEY).build();
    }

    @Bean
    Queue videoDeadLetterQueue() {
        return QueueBuilder.durable(MessagingConstants.QUEUE_VIDEO_DLQ).build();
    }

    @Bean
    Binding videoBinding(Queue videoQueue, TopicExchange oepExchange) {
        return BindingBuilder.bind(videoQueue).to(oepExchange).with(MessagingConstants.RK_VIDEO_TRANSCODE);
    }

    @Bean
    Binding videoDeadLetterBinding(Queue videoDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(videoDeadLetterQueue).to(deadLetterExchange).with(MessagingConstants.DLQ_VIDEO_KEY);
    }

    /** prefetch 1 + one consumer: a transcoding worker never holds more than one video. */
    @Bean
    org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory videoListenerFactory(
            org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer configurer,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
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
