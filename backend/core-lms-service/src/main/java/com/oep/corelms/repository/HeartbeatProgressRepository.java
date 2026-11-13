package com.oep.corelms.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HeartbeatProgressRepository {
    private final JdbcTemplate jdbc;

    public java.util.List<UUID> remedialAttempts(UUID enrollment, UUID video) {
        return jdbc.queryForList("""
                select a.id from lesson_contents v join lessons target on target.id = v.lesson_id
                join lessons anchor on anchor.module_id = target.module_id
                join quizzes z on z.lesson_id = anchor.id and z.assessment_kind = 'POST_TEST'
                join quiz_attempts a on a.quiz_id = z.id and a.enrollment_id = ?
                join enrollments e on e.id = a.enrollment_id and e.course_id = z.course_id
                left join quiz_results r on r.attempt_id = a.id
                cross join lateral (select sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) as total
                    from jsonb_array_elements(a.snapshot_payload -> 'items') item(value)) points
                where v.id = ? and a.attempt_number = greatest(z.max_attempts, 1)
                  and not a.is_remediation
                  and ((a.status = 'GRADED' and r.final_score < points.total * 0.7)
                    or (a.status = 'IN_PROGRESS' and a.snapshot_payload ->> 'expiresAt' is not null
                        and cast(a.snapshot_payload ->> 'expiresAt' as timestamptz) <= now()))
                  and not exists (select 1 from quiz_attempts later where later.enrollment_id = e.id
                      and later.quiz_id = z.id and later.attempt_number > a.attempt_number)
                """, UUID.class, enrollment, video);
    }

    public Optional<Integer> videoDuration(UUID student, UUID enrollment, UUID lesson, UUID video) {
        return jdbc.query("""
                select c.duration_seconds from enrollments e
                join users u on u.id = e.student_id
                join course_modules m on m.course_id = e.course_id
                join lessons l on l.module_id = m.id
                join lesson_contents c on c.lesson_id = l.id
                where e.id = ? and e.student_id = ? and e.status = 'ACTIVE'
                  and (e.access_expires_at is null or e.access_expires_at >= now())
                  and u.role = 'STUDENT' and u.account_status = 'ACTIVE'
                  and l.id = ? and c.id = ? and c.content_type = 'VIDEO'
                  and c.upload_status = 'AVAILABLE' and c.duration_seconds > 0
                """, (row, index) -> row.getInt(1), enrollment, student, lesson, video)
                .stream().findFirst();
    }

    public boolean canWrite(UUID student, UUID enrollment) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from enrollments e join users u on u.id = e.student_id
                    where e.id = ? and e.student_id = ? and e.status = 'ACTIVE'
                      and (e.access_expires_at is null or e.access_expires_at >= now())
                      and u.role = 'STUDENT' and u.account_status = 'ACTIVE'
                )
                """, Boolean.class, enrollment, student));
    }

    public int persist(UUID batch, String rows) {
        // The ledger insert and the aggregate write commit in the same statement.
        return jdbc.update("""
                with accepted as (
                    insert into heartbeat_flush_batches (id, flushed_at, progress_rows, heartbeat_count)
                    select ?, clock_timestamp(), count(*), sum(events)
                    from jsonb_to_recordset(cast(? as jsonb)) as e(events integer)
                    on conflict (id) do nothing
                    returning id
                )
                insert into lesson_progress as p
                    (id, enrollment_id, lesson_id, status, time_spent_seconds, last_content_id,
                     started_at, last_accessed_at, video_position_seconds)
                select gen_random_uuid(), e.enrollment, e.lesson, 'IN_PROGRESS', e.seconds, c.id,
                       to_timestamp(e.first_ms / 1000.0), to_timestamp(e.last_ms / 1000.0), e.position
                from jsonb_to_recordset(cast(? as jsonb)) as e(
                    enrollment uuid, lesson uuid, content uuid, seconds integer, first_ms bigint, last_ms bigint, position integer)
                join enrollments en on en.id = e.enrollment
                join course_modules m on m.course_id = en.course_id
                join lessons l on l.id = e.lesson and l.module_id = m.id
                left join lesson_contents c on c.id = e.content and c.lesson_id = l.id
                cross join accepted
                order by e.enrollment, e.lesson
                on conflict (enrollment_id, lesson_id) do update set
                    time_spent_seconds = least(2147483647, p.time_spent_seconds::bigint + excluded.time_spent_seconds),
                    status = case when p.status = 'NOT_STARTED' then 'IN_PROGRESS' else p.status end,
                    started_at = coalesce(p.started_at, excluded.started_at),
                    last_content_id = case when excluded.last_accessed_at >= p.last_accessed_at
                        then coalesce(excluded.last_content_id, p.last_content_id) else p.last_content_id end,
                    video_position_seconds = case when excluded.last_accessed_at >= p.last_accessed_at
                        then excluded.video_position_seconds else p.video_position_seconds end,
                    last_accessed_at = greatest(p.last_accessed_at, excluded.last_accessed_at)
                """, batch, rows, rows);
    }
}
