package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.entity.notification.Notification;
import com.oep.coremodel.entity.notification.NotificationPreference;
import com.oep.coremodel.event.NotificationRequestedEvent;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import com.oep.worker.repository.EmailNotificationEventRepository;
import com.oep.worker.repository.NotificationPreferenceRepository;
import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final NotificationRepository notifications;
    private final EmailNotificationEventRepository emailEvents;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;

    @RabbitListener(queues = MessagingConstants.QUEUE_NOTIFICATION)
    public void onNotification(NotificationRequestedEvent event) {
        if (event.userId() == null || event.eventType() == null || event.eventType().isBlank()
                || event.title() == null || event.title().isBlank()
                || event.message() == null || event.message().isBlank()) {
            throw new IllegalArgumentException("Notification event requires user, type, title, and message");
        }
        var user = users.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("Notification recipient does not exist: " + event.userId()));
        NotificationPreference preference = preferences.findByUser_IdAndEventType(user.getId(), event.eventType())
                .or(() -> preferences.findByUser_IdAndEventType(user.getId(), "*")).orElse(null);
        String dedupeKey = event.dedupeKey();
        if (dedupeKey == null || dedupeKey.isBlank()) {
            dedupeKey = event.eventType() + ":" + (event.targetId() == null ? "global" : event.targetId()) + ":" + event.userId();
        }

        if ((preference == null || preference.isInAppEnabled()) && !notifications.existsByDedupeKey(dedupeKey)) {
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setEventType(event.eventType());
            notification.setTitle(event.title());
            notification.setMessage(event.message());
            notification.setDedupeKey(dedupeKey);
            notification.setTargetType(event.targetType());
            notification.setTargetId(event.targetId());
            notification.setActionUrl(event.actionUrl());
            notification.setMetadata(event.metadata());
            notification.setIsRead(false);
            try {
                notifications.saveAndFlush(notification);
            } catch (DataIntegrityViolationException duplicate) {
                log.debug("Duplicate in-app notification suppressed for {}", dedupeKey);
            }
        }

        if (preference == null || preference.isEmailEnabled()) {
            String recipient = user.getEmail();
            if (recipient != null && !recipient.isBlank()) {
                String emailKey = dedupeKey.length() <= 248 ? dedupeKey + ":email"
                        : "email:" + UUID.nameUUIDFromBytes(dedupeKey.getBytes(StandardCharsets.UTF_8));
                if (!emailEvents.existsByDedupeKey(emailKey)) {
                    EmailNotificationEvent delivery = new EmailNotificationEvent();
                    delivery.setUser(user);
                    delivery.setEventType(event.eventType());
                    delivery.setRecipientEmail(recipient);
                    delivery.setSubject(event.title());
                    delivery.setMessage(event.message());
                    delivery.setActionUrl(event.actionUrl());
                    delivery.setLocale("vi-VN");
                    delivery.setStatus(NotificationDeliveryStatus.PENDING);
                    delivery.setScheduledAt(Instant.now());
                    delivery.setDedupeKey(emailKey);
                    try {
                        emailEvents.saveAndFlush(delivery);
                    } catch (DataIntegrityViolationException duplicate) {
                        log.debug("Duplicate email notification suppressed for {}", emailKey);
                    }
                }
            }
        }
        log.info("Notification processed for user {}: {}", event.userId(), event.title());
    }
}
