package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    public record Overview(long totalCourses, long totalEnrollments, long completedEnrollments,
                           String databaseStatus, String redisStatus,
                           long pendingOutboxEvents, long failedEmailDeliveries) {}

    @Transactional(readOnly = true)
    public Overview get() {
        Overview counts = jdbc.queryForObject("""
                select (select count(*) from courses) as courses,
                       (select count(*) from enrollments) as enrollments,
                       (select count(*) from enrollments where status = 'COMPLETED') as completed,
                       (select count(*) from outbox_events where status = 'PENDING') as pending_outbox,
                       (select count(*) from email_notification_events where status = 'FAILED') as failed_email
                """, (rs, row) -> new Overview(rs.getLong("courses"), rs.getLong("enrollments"),
                rs.getLong("completed"), "UP", redisStatus(), rs.getLong("pending_outbox"), rs.getLong("failed_email")));
        return counts;
    }

    private String redisStatus() {
        RedisConnection connection = null;
        try {
            connection = redis.getConnectionFactory().getConnection();
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "DOWN";
        } catch (RuntimeException unavailable) {
            return "DOWN";
        } finally {
            if (connection != null) connection.close();
        }
    }
}
