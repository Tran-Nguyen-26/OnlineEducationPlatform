package com.oep.corelms.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data-retention policy, once a day (03:00): keep operational tables from growing forever.
 * Academic records (enrollments, grades, certificates) and the audit trail are NOT touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final JdbcTemplate jdbc;

    @Value("${oep.retention.notification-days:180}") private int notificationDays;
    @Value("${oep.retention.outbox-days:7}") private int outboxDays;
    @Value("${oep.retention.refresh-token-days:30}") private int refreshTokenDays;

    @Scheduled(cron = "${oep.retention.cron:0 0 3 * * *}")
    @SchedulerLock(name = "retentionCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanup() {
        int n = jdbc.update("delete from notifications where created_at < now() - make_interval(days => ?)", notificationDays);
        int o = jdbc.update("delete from outbox_events where status = 'PROCESSED' and processed_at < now() - make_interval(days => ?)", outboxDays);
        int t = jdbc.update("delete from refresh_tokens where expires_at < now() - make_interval(days => ?)", refreshTokenDays);
        log.info("Retention cleanup removed {} notifications, {} delivered outbox events, {} expired refresh tokens", n, o, t);
    }
}
