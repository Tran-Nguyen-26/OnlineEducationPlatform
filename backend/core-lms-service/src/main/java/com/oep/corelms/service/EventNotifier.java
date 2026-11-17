package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.coremodel.event.OutboxEvents;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Queues an in-app notification through the transactional outbox: the row is written in the SAME transaction as the
 * business change (so a rollback never leaks a notification, and a RabbitMQ outage never fails the business call);
 * worker-service's relay publishes it to RabbitMQ with publisher confirms and retries with backoff.
 */
@Component
@RequiredArgsConstructor
public class EventNotifier {

    @PersistenceContext
    private EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void publish(NotificationRequestedEvent event) {
        em.persist(OutboxEvents.notification(event, mapper));
    }

    public void send(UUID userId, String type, String title, String message, String dedupeKey, UUID courseId) {
        publish(new NotificationRequestedEvent(userId, type, title, message, dedupeKey, "COURSE", courseId, Instant.now()));
    }
}
