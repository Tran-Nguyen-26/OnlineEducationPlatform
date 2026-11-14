package com.oep.worker.service;

import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import com.oep.worker.repository.EmailNotificationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEmailDeliveryJob {
    private static final int MAX_ATTEMPTS = 4; // Initial send plus three retries.
    private final EmailNotificationEventRepository events;
    private final EmailService emailService;

    @Scheduled(fixedDelayString = "${oep.outbox.email-poll-ms:5000}")
    @Transactional
    public void deliver() {
        for (EmailNotificationEvent event : events.findDue(NotificationDeliveryStatus.PENDING, Instant.now(), PageRequest.of(0, 20))) {
            deliverOne(event.getId());
        }
    }

    public void deliverOne(java.util.UUID id) {
        EmailNotificationEvent event = events.findById(id).orElse(null);
        if (event == null || event.getStatus() != NotificationDeliveryStatus.PENDING
                || event.getScheduledAt() == null || event.getScheduledAt().isAfter(Instant.now())) return;
        try {
            emailService.sendNotificationEmail(event.getRecipientEmail(), event.getSubject(), event.getMessage(),
                    event.getActionUrl(), event.getLocale());
            event.setStatus(NotificationDeliveryStatus.SENT);
            event.setSentAt(Instant.now());
            event.setLastError(null);
        } catch (RuntimeException ex) {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setLastError(ex.getMessage() == null ? "Email delivery failed" : ex.getMessage().substring(0, Math.min(1000, ex.getMessage().length())));
            if (retries >= MAX_ATTEMPTS) {
                event.setStatus(NotificationDeliveryStatus.FAILED);
                log.error("Notification email permanently failed after {} retries; eventId={}, dedupeKey={}", retries, id, event.getDedupeKey());
            } else {
                event.setScheduledAt(Instant.now().plus(15L << (retries - 1), ChronoUnit.SECONDS));
                log.warn("Notification email attempt {} failed; will retry at {} for eventId={}", retries, event.getScheduledAt(), id);
            }
        }
        events.save(event);
    }
}
