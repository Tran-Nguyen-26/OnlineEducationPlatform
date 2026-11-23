package com.oep.worker.listener;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import com.oep.worker.repository.EmailNotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Best-effort e-mail delivery of a notification (BRD: e-mail alongside in-app). Disabled unless oep.mail.enabled=true.
 * The in-app notification is the source of truth: a mail failure is recorded in email_notification_events as FAILED
 * and never rolls back or blocks the in-app row. Automatic retries are not implemented (single attempt).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final EmailNotificationEventRepository events;

    @Value("${oep.mail.enabled:false}")
    private boolean enabled;

    @Value("${oep.mail.from:no-reply@oep.local}")
    private String from;

    public void send(User user, String eventType, String subject, String body) {
        if (!enabled || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        EmailNotificationEvent record = new EmailNotificationEvent();
        record.setUser(user);
        record.setEventType(eventType);
        record.setRecipientEmail(user.getEmail());
        record.setScheduledAt(Instant.now());
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(user.getEmail());
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            record.setStatus(NotificationDeliveryStatus.SENT);
            record.setSentAt(Instant.now());
        } catch (RuntimeException e) {
            log.warn("E-mail for {} to user {} failed: {}", eventType, user.getId(), e.getMessage());
            record.setStatus(NotificationDeliveryStatus.FAILED);
            record.setRetryCount(1);
        }
        events.save(record);
    }
}
