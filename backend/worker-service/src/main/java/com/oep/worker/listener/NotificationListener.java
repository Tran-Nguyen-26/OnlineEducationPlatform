package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final com.oep.worker.repository.NotificationPreferenceRepository preferences;

    /** dedupeKey carries a unique DB constraint, so a redelivered message never creates a duplicate row. */
    @Transactional
    @RabbitListener(queues = MessagingConstants.QUEUE_NOTIFICATION)
    public void onNotification(NotificationRequestedEvent event) {
        if (notifications.existsByDedupeKey(event.dedupeKey())) {
            log.info("Duplicate notification delivery ignored: {}", event.dedupeKey());
            return;
        }
        String category = com.oep.coremodel.event.NotificationCategory.of(event.eventType());
        if (category != null && preferences.findByUser_IdAndEventType(event.userId(), category).map(p -> !p.isInAppEnabled()).orElse(false)) {
            log.info("Notification {} skipped: user switched off category {}", event.dedupeKey(), category);
            return;
        }
        Notification notification = new Notification();
        notification.setUser(users.getReferenceById(event.userId()));
        notification.setEventType(event.eventType());
        notification.setTitle(event.title());
        notification.setMessage(event.message());
        notification.setDedupeKey(event.dedupeKey());
        notification.setTargetType(event.targetType());
        notification.setTargetId(event.targetId());
        try {
            notifications.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate notification delivery (race) ignored: {}", event.dedupeKey());
        }
    }
}
