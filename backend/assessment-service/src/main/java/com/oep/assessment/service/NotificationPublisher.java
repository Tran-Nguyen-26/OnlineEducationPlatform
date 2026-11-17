package com.oep.assessment.service;

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

/** Queues a notification through the transactional outbox (same transaction as the grading/submission change). */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

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
