package com.oep.worker.service;

import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled job to prevent database table bloat and storage leaks:
 * 1. Purges PROCESSED outbox events older than 30 days.
 * 2. Purges read notifications older than 90 days (utilizing idx_notifications_cleanup).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionCleanupJob {

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;

    /**
     * Executes daily at 02:00 AM server time.
     */
    @Scheduled(cron = "${oep.retention.cron:0 0 2 * * *}")
    @Transactional
    public void purgeOldRecords() {
        log.info("Starting scheduled data retention cleanup job...");

        // 1. Purge PROCESSED outbox events older than 30 days
        Instant outboxCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deletedOutbox = outboxEventRepository.deleteProcessedEventsOlderThan(outboxCutoff);
        log.info("Data Retention: Purged {} PROCESSED outbox events older than 30 days", deletedOutbox);

        // 2. Purge read notifications older than 90 days
        Instant notifCutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int deletedNotifs = notificationRepository.deleteReadNotificationsOlderThan(notifCutoff);
        log.info("Data Retention: Purged {} read notifications older than 90 days", deletedNotifs);
    }
}
