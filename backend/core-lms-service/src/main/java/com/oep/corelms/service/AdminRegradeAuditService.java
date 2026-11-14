package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRegradeAuditService {
    private final JdbcTemplate jdbc;

    public record Regrade(UUID submissionId, String courseTitle, BigDecimal originalScore,
                          BigDecimal finalScore, BigDecimal difference, BigDecimal maxScore,
                          String criterion, String reason, String reviewerEmail,
                          Instant regradedAt, boolean exceedsAlertThreshold) {}

    @Transactional(readOnly = true)
    public List<Regrade> list() {
        return jdbc.query("""
                select a.target_id, c.title,
                       cast(a.metadata ->> 'originalScore' as numeric),
                       cast(a.metadata ->> 'finalScore' as numeric),
                       abs(cast(a.metadata ->> 'finalScore' as numeric) - cast(a.metadata ->> 'originalScore' as numeric)),
                       ass.max_score, a.metadata ->> 'criterion', a.metadata ->> 'reason',
                       reviewer.email, a.created_at
                from audit_logs a
                join users reviewer on reviewer.id = a.actor_id
                left join assignment_submissions s on s.id = a.target_id
                left join assignments ass on ass.id = s.assignment_id
                left join courses c on c.id = ass.course_id
                where a.action = 'REGRADE' and a.target_type = 'ASSIGNMENT_SUBMISSION'
                order by a.created_at desc
                limit 500
                """, (rs, row) -> {
            BigDecimal difference = rs.getBigDecimal(5);
            BigDecimal maxScore = rs.getBigDecimal(6);
            boolean exceeds = difference != null && maxScore != null
                    && difference.compareTo(maxScore.multiply(new BigDecimal("0.15"))) > 0;
            return new Regrade(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3),
                    rs.getBigDecimal(4), difference, maxScore, rs.getString(7), rs.getString(8),
                    rs.getString(9), rs.getTimestamp(10).toInstant(), exceeds);
        });
    }
}
