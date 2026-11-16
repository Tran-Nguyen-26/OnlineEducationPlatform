package com.oep.corelms.service;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Queues an in-app notification (persisted idempotently by the worker via dedupeKey). */
@Component
@RequiredArgsConstructor
public class EventNotifier {

    private final RabbitTemplate rabbitTemplate;

    public void send(UUID userId, String type, String title, String message, String dedupeKey, UUID courseId) {
        rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_NOTIFICATION,
                new NotificationRequestedEvent(userId, type, title, message, dedupeKey, "COURSE", courseId, Instant.now()));
    }
}
