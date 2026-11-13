package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutcomeMasteryService {
    private final JdbcTemplate jdbc;

    public record Outcome(String code, String description, String bloom, BigDecimal percent,
                          int quizItems, int rubricCriteria) {}

    @Transactional(readOnly = true)
    public List<Outcome> forLearner(UUID actor, UUID course) {
        UUID enrollment = jdbc.queryForList("""
                select e.id from enrollments e join users u on u.id = e.student_id
                join courses c on c.id = e.course_id
                where e.student_id = ? and e.course_id = ? and e.status in ('ACTIVE', 'COMPLETED')
                  and u.role = 'STUDENT' and u.account_status = 'ACTIVE'
                  and c.lifecycle_status in ('PUBLISHED', 'ARCHIVED')
                """, UUID.class, actor, course).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem kết quả khóa học"));
        return jdbc.query("""
                with latest as (
                    select distinct on (z.id) a.id, a.snapshot_payload
                    from quizzes z join quiz_attempts a on a.quiz_id = z.id
                    join quiz_results r on r.attempt_id = a.id
                    where z.course_id = ? and a.enrollment_id = ? and a.status = 'GRADED'
                      and z.assessment_kind in ('MINI_QUIZ', 'POST_TEST', 'MASS_EXAM')
                      and z.show_score is distinct from false
                    order by z.id, a.attempt_number desc
                ), quiz_evidence as (
                    select cast(item.value ->> 'outcomeId' as uuid) as lo_id,
                        coalesce(answer.awarded_score, 0) as earned,
                        cast(item.value -> 'prompt' ->> 'points' as numeric) as possible
                    from latest a
                    cross join lateral jsonb_array_elements(a.snapshot_payload -> 'items') item(value)
                    left join quiz_answers answer on answer.attempt_id = a.id
                      and answer.question_id = cast(item.value -> 'prompt' ->> 'id' as uuid)
                    where item.value ->> 'outcomeId' is not null
                ), rubric_evidence as (
                    select lo.id as lo_id,
                        cast(g.rubric_scores ->> (criterion.value ->> 'code') as numeric) as earned,
                        cast(criterion.value ->> 'maxScore' as numeric) as possible
                    from assignments a join assignment_submissions s on s.assignment_id = a.id
                    join assignment_grades g on g.submission_id = s.id
                    cross join lateral jsonb_array_elements(a.rubric) criterion(value)
                    join learning_outcomes lo on lo.course_id = a.course_id and lo.code = criterion.value ->> 'learningOutcome'
                    where a.course_id = ? and s.enrollment_id = ? and s.status in ('GRADED', 'PENDING_REGRADE')
                      and jsonb_exists(g.rubric_scores, criterion.value ->> 'code')
                ), evidence as (
                    select lo_id, earned, possible, 1 as quiz_item, 0 as criterion from quiz_evidence where possible > 0
                    union all
                    select lo_id, earned, possible, 0, 1 from rubric_evidence where possible > 0 and earned is not null
                )
                select lo.code, lo.description, lo.bloom_level,
                       trunc(100 * sum(e.earned) / nullif(sum(e.possible), 0), 2),
                       coalesce(sum(e.quiz_item), 0), coalesce(sum(e.criterion), 0)
                from learning_outcomes lo left join evidence e on e.lo_id = lo.id
                where lo.course_id = ?
                group by lo.id, lo.code, lo.description, lo.bloom_level order by lo.code
                """,
                (row, index) -> new Outcome(row.getString(1), row.getString(2), row.getString(3),
                        row.getBigDecimal(4), row.getInt(5), row.getInt(6)), course, enrollment, course, enrollment, course);
    }
}
