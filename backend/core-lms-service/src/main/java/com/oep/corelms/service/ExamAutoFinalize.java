package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamAutoFinalize {
    private final JdbcTemplate jdbc;
    private final LearnerQuizService quizzes;

    @Scheduled(fixedDelayString = "${oep.exam.finalize-ms:2000}")
    public void collect() {
        Instant now = Instant.now();
        var due = jdbc.query("""
                select a.id, e.course_id, e.student_id from quiz_attempts a
                join enrollments e on e.id = a.enrollment_id
                where a.status = 'IN_PROGRESS' and a.exam_cutoff_at <= ?
                order by a.exam_cutoff_at, a.id limit 50
                """, (row, index) -> new Due(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getObject(3, UUID.class)), java.sql.Timestamp.from(now.minusSeconds(ExamWindow.GRACE_SECONDS)));
        for (Due attempt : due) {
            try {
                quizzes.finalizeExam(attempt.id(), attempt.course(), attempt.student(), now);
            } catch (RuntimeException ex) {
                log.warn("Exam auto-finalize failed for attempt {}", attempt.id(), ex);
            }
        }
    }

    private record Due(UUID id, UUID course, UUID student) {}
}
