package com.oep.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MassExamReminderJob {
    private final JdbcTemplate jdbc;

    /** Enqueues one reminder about 15 minutes before each scheduled mass exam. */
    @Scheduled(fixedDelayString = "${oep.exam.reminder-poll-ms:60000}")
    @Transactional
    public void enqueueUpcomingReminders() {
        int enqueued = jdbc.update("""
                insert into outbox_events
                  (id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
                select gen_random_uuid(), 'QUIZ', q.id, 'NOTIFICATION',
                       jsonb_build_object(
                         'eventType', 'MASS_EXAM_REMINDER',
                         'targetType', 'QUIZ',
                         'targetId', q.id::text,
                         'recipientUserId', e.student_id::text,
                         'title', 'Kỳ thi đồng loạt sắp bắt đầu',
                         'message', 'Kỳ thi “' || q.title || '” sẽ bắt đầu lúc ' ||
                           to_char(q.exam_starts_at at time zone 'Asia/Ho_Chi_Minh', 'DD/MM/YYYY HH24:MI') ||
                           '. Hãy vào phòng thi trước giờ bắt đầu.',
                         'actionUrl', '/assessment',
                         'dedupeKey', 'MASS_EXAM_REMINDER:' || q.id::text || ':' || e.student_id::text,
                         'metadata', jsonb_build_object('courseId', q.course_id::text,
                           'quizId', q.id::text, 'startsAt', q.exam_starts_at)
                       ), 'PENDING', 0, now()
                from quizzes q join enrollments e on e.course_id = q.course_id
                where q.assessment_kind = 'MASS_EXAM'
                  and q.exam_starts_at > now() + interval '14 minutes'
                  and q.exam_starts_at <= now() + interval '15 minutes'
                  and e.status in ('ACTIVE', 'COMPLETED')
                  and not exists (
                    select 1 from outbox_events existing
                    where existing.event_type = 'NOTIFICATION'
                      and existing.payload ->> 'dedupeKey' =
                        'MASS_EXAM_REMINDER:' || q.id::text || ':' || e.student_id::text
                  )
                """);
        if (enqueued > 0) log.info("Queued {} upcoming mass-exam reminders", enqueued);
    }
}
