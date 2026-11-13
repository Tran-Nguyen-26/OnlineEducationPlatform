package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonMasteryService {
    private final JdbcTemplate jdbc;
    private final HeartbeatBuffer heartbeat;

    public record Evidence(UUID id, String title, boolean video, boolean documents, boolean miniQuiz, boolean fastTrack) {}
    public record Lesson(UUID id, String title, boolean unlocked, boolean video, boolean documents,
                         boolean miniQuiz, boolean fastTrack) {}

    public List<Lesson> lessons(UUID actor, UUID course) {
        List<Snapshot> snapshots = jdbc.query("""
                with access as (
                    select e.id from enrollments e join users u on u.id = e.student_id
                    join courses c on c.id = e.course_id
                    where e.student_id = ? and e.course_id = ? and e.status = 'ACTIVE'
                      and u.account_status = 'ACTIVE' and u.role = 'STUDENT' and c.lifecycle_status = 'PUBLISHED'
                      and (e.access_expires_at is null or e.access_expires_at >= now())
                ), pretest_mastery as (
                    select cast(item.value ->> 'outcomeId' as uuid) as lo_id
                    from access e join quiz_attempts a on a.enrollment_id = e.id
                    join quizzes z on z.id = a.quiz_id and z.assessment_kind = 'PRE_TEST' and z.course_id = ?
                    join quiz_results r on r.attempt_id = a.id
                    cross join lateral jsonb_array_elements(a.snapshot_payload -> 'items') item(value)
                    left join quiz_answers answer on answer.attempt_id = a.id
                      and answer.question_id = cast(item.value -> 'prompt' ->> 'id' as uuid)
                    where a.attempt_number = 1 and a.status = 'GRADED' and item.value ->> 'outcomeId' is not null
                    group by a.id, item.value ->> 'outcomeId'
                    having sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) > 0
                       and sum(coalesce(answer.awarded_score, 0)) >= sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) * 0.85
                )
                select l.id, l.title, e.id,
                    not exists (
                        select 1 from lesson_contents d where d.lesson_id = l.id
                          and d.content_type in ('PDF', 'DOCUMENT')
                          and not exists (select 1 from lesson_document_reads dr
                            where dr.enrollment_id = e.id and dr.content_id = d.id)
                    ),
                    exists (
                        select 1 from quizzes z join quiz_attempts a on a.quiz_id = z.id
                        join quiz_results r on r.attempt_id = a.id
                        cross join lateral (select sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) as total
                            from jsonb_array_elements(a.snapshot_payload -> 'items') item(value)) points
                        where z.lesson_id = l.id and z.course_id = ? and z.assessment_kind = 'MINI_QUIZ'
                          and a.enrollment_id = e.id and a.status = 'GRADED'
                          and points.total > 0 and r.final_score >= points.total * 0.7
                    ),
                    exists (
                        select 1 from lesson_contents material join pretest_mastery mastery on mastery.lo_id = material.lo_id
                        where material.lesson_id = l.id
                    ),
                    coalesce(array(select v.id from lesson_contents v where v.lesson_id = l.id
                        and v.content_type = 'VIDEO' order by v.id), '{}'),
                    coalesce(array(select case when v.upload_status = 'AVAILABLE' then coalesce(v.duration_seconds, 0) else 0 end
                        from lesson_contents v where v.lesson_id = l.id and v.content_type = 'VIDEO' order by v.id), '{}')
                from access e cross join course_modules m join lessons l on l.module_id = m.id
                where m.course_id = ?
                order by m.position, m.id, l.position, l.id
                """, (row, index) -> new Snapshot(row.getObject(1, UUID.class), row.getString(2), row.getObject(3, UUID.class),
                row.getBoolean(4), row.getBoolean(5), row.getBoolean(6),
                (UUID[]) row.getArray(7).getArray(), (Integer[]) row.getArray(8).getArray()),
                actor, course, course, course, course);
        return evaluate(snapshots.stream().map(item -> new Evidence(item.id(), item.title(), videosComplete(item),
                item.documents(), item.miniQuiz(), item.fastTrack())).toList());
    }

    private boolean videosComplete(Snapshot item) {
        for (int index = 0; index < item.videos().length; index++) {
            int duration = item.durations()[index];
            if (duration <= 0 || heartbeat.watchedSeconds(item.enrollment(), item.videos()[index]) * 5 < duration * 4L) return false;
        }
        return true;
    }

    private record Snapshot(UUID id, String title, UUID enrollment, boolean documents, boolean miniQuiz,
                            boolean fastTrack, UUID[] videos, Integer[] durations) {}

    static List<Lesson> evaluate(List<Evidence> evidence) {
        List<Lesson> result = new ArrayList<>();
        boolean sequential = true;
        for (Evidence item : evidence) {
            result.add(new Lesson(item.id(), item.title(), sequential || item.fastTrack(),
                    item.video(), item.documents(), item.miniQuiz(), item.fastTrack()));
            sequential = sequential && (item.fastTrack() || (item.video() && item.documents() && item.miniQuiz()));
        }
        return List.copyOf(result);
    }

    public void requireUnlocked(UUID actor, UUID course, UUID lesson) {
        if (lessons(actor, course).stream().noneMatch(item -> item.id().equals(lesson) && item.unlocked())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bài học chưa mở hoặc quyền học đã hết hạn");
        }
    }
}
