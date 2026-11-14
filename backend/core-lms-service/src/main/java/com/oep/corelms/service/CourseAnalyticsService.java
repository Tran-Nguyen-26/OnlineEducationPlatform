package com.oep.corelms.service;

import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CourseAnalyticsService {
    private final JdbcTemplate jdbc;
    private final CourseInstructorRepository instructors;
    private final UserRepository users;
    private final NotificationPublisher notifications;
    private final ExamPanicCircuitBreaker examPanicCircuitBreaker;

    public enum IssueCategory { CONTENT, RUBRIC }

    public record LessonMetrics(UUID id, String title, long started, long completed) {}
    public record RewindBucket(UUID videoId, String videoTitle, UUID lessonId, String lessonTitle,
                               int bucketStartSec, long rewinds) {}
    public record Misconception(UUID questionId, String question, String learningOutcome,
                                String distractor, long wrongSelections) {}
    public record ScoreBand(int from, int to, long learners) {}
    public record AssessmentMetrics(long pairedLearners, BigDecimal learningGain, List<ScoreBand> postTestScores) {}
    public record Analytics(long learners, long completed, List<LessonMetrics> lessons, AssessmentMetrics assessments) {}

    @Transactional(readOnly = true)
    public Analytics analytics(UUID actor, UUID course) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        requireViewer(actor, course);
        long[] counts = jdbc.queryForObject("""
                select count(*), count(*) filter (where completed_at is not null or status = 'COMPLETED')
                from enrollments
                where course_id = ? and status not in ('WITHDRAWN', 'REMOVED')
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, course);

        List<LessonMetrics> lessons = jdbc.query("""
                with progress_counts as (
                    select p.lesson_id,
                           count(*) filter (where p.status in ('IN_PROGRESS', 'COMPLETED')) as started,
                           count(*) filter (where p.status = 'COMPLETED') as completed
                    from enrollments e
                    join lesson_progress p on p.enrollment_id = e.id
                    where e.course_id = ? and e.status not in ('WITHDRAWN', 'REMOVED')
                    group by p.lesson_id
                )
                select l.id, l.title, coalesce(p.started, 0), coalesce(p.completed, 0)
                from course_modules m
                join lessons l on l.module_id = m.id
                left join progress_counts p on p.lesson_id = l.id
                where m.course_id = ?
                order by m.position, l.position, l.id
                """, (rs, row) -> new LessonMetrics(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getLong(3), rs.getLong(4)), course, course);
        return new Analytics(counts[0], counts[1], lessons, assessmentMetrics(course));
    }

    @Transactional(readOnly = true)
    public List<RewindBucket> rewindHeatmap(UUID actor, UUID course) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        requireViewer(actor, course);
        return jdbc.query("""
                select h.video_id, v.title, h.lesson_id, l.title, h.bucket_start_sec, h.rewind_count
                from video_rewind_heatmap h join lesson_contents v on v.id = h.video_id
                join lessons l on l.id = h.lesson_id
                where h.course_id = ?
                order by h.rewind_count desc, h.bucket_start_sec, h.video_id
                limit 100
                """, (rs, row) -> new RewindBucket(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5), rs.getLong(6)), course);
    }

    @Transactional(readOnly = true)
    public List<Misconception> misconceptionRadar(UUID actor, UUID course) {
        examPanicCircuitBreaker.requireAncillaryServicesAvailable();
        requireViewer(actor, course);
        return jdbc.query("""
                select q.id, q.question_text, lo.code, o.option_text, count(*) as wrong_selections
                from quiz_answers a join quiz_attempts at on at.id = a.attempt_id
                join quizzes z on z.id = at.quiz_id and z.course_id = ?
                join questions q on q.id = a.question_id
                left join learning_outcomes lo on lo.id = q.lo_id
                join lateral jsonb_array_elements_text(case when jsonb_typeof(a.selected_option_data) = 'array'
                    then a.selected_option_data else '[]'::jsonb end) selected(option_id) on true
                join question_options o on o.id::text = selected.option_id and o.question_id = q.id and not o.is_correct
                where at.status = 'GRADED' and a.correct = false
                group by q.id, q.question_text, lo.code, o.id, o.option_text
                order by wrong_selections desc, q.id
                limit 100
                """, (rs, row) -> new Misconception(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getLong(5)), course);
    }

    private AssessmentMetrics assessmentMetrics(UUID course) {
        return jdbc.queryForObject("""
                with graded as (
                    select e.id as enrollment, z.id as quiz, z.assessment_kind as kind,
                           z.grading_policy, a.attempt_number, r.final_score * 100 / points.total as percent
                    from enrollments e
                    join quiz_attempts a on a.enrollment_id = e.id and a.status = 'GRADED'
                    join quizzes z on z.id = a.quiz_id and z.course_id = e.course_id
                    join quiz_results r on r.attempt_id = a.id
                    cross join lateral (
                        select sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) as total
                        from jsonb_array_elements(a.snapshot_payload -> 'items') item(value)
                    ) points
                    where e.course_id = ? and e.status in ('ACTIVE','COMPLETED') and points.total > 0
                      and z.assessment_kind in ('PRE_TEST','POST_TEST')
                ), by_quiz as (
                    select enrollment, quiz, kind, case grading_policy
                        when 'LATEST' then (array_agg(percent order by attempt_number desc))[1]
                        when 'AVERAGE' then avg(percent)
                        else max(percent) end as percent
                    from graded group by enrollment, quiz, kind, grading_policy
                ), by_learner as (
                    select enrollment, avg(percent) filter (where kind = 'PRE_TEST') as pre,
                           avg(percent) filter (where kind = 'POST_TEST') as post
                    from by_quiz group by enrollment
                )
                select count(*) filter (where pre is not null and post is not null),
                       round(avg(post - pre), 2),
                       count(*) filter (where post < 50),
                       count(*) filter (where post >= 50 and post < 70),
                       count(*) filter (where post >= 70 and post < 85),
                       count(*) filter (where post >= 85)
                from by_learner
                """, (row, index) -> new AssessmentMetrics(row.getLong(1), row.getBigDecimal(2), List.of(
                new ScoreBand(0, 50, row.getLong(3)), new ScoreBand(50, 70, row.getLong(4)),
                new ScoreBand(70, 85, row.getLong(5)), new ScoreBand(85, 100, row.getLong(6)))), course);
    }

    public static String csv(Analytics metrics) {
        StringBuilder report = new StringBuilder("\uFEFFlesson_id,lesson,learners,started,completed,completion_percent,unfinished_percent\r\n");
        for (LessonMetrics lesson : metrics.lessons()) {
            String rate = metrics.learners() == 0 ? "" : String.format(Locale.ROOT, "%.2f",
                    100.0 * lesson.completed() / metrics.learners());
            report.append(lesson.id()).append(',').append(csvText(lesson.title())).append(',')
                    .append(metrics.learners()).append(',').append(lesson.started()).append(',')
                    .append(lesson.completed()).append(',').append(rate).append(',')
                    .append(lesson.started() == 0 ? "" : String.format(Locale.ROOT, "%.2f",
                            100.0 * (lesson.started() - lesson.completed()) / lesson.started())).append("\r\n");
        }
        return report.toString();
    }

    @Transactional
    public void flag(UUID actor, UUID course, IssueCategory category, String detail) {
        requireInstructor(actor, course);
        var admins = users.findByRoleAndAccountStatus(UserRole.ADMIN, AccountStatus.ACTIVE);
        if (admins.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No active administrator can receive this issue");
        }
        UUID issue = UUID.randomUUID();
        jdbc.update("""
                insert into course_issue_flags (id, course_id, instructor_id, category, detail, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'OPEN', now(), now())
                """, issue, course, actor, category.name(), detail.strip());
        for (var admin : admins) {
            notifications.publishNotificationEvent("COURSE_ISSUE_FLAGGED", "COURSE_ISSUE", issue,
                    admin.getId(), "Báo lỗi khóa học", detail.strip(), null,
                    Map.of("courseId", course, "category", category.name(), "reportedBy", actor));
        }
    }

    private void requireInstructor(UUID actor, UUID course) {
        var user = users.findById(actor).orElseThrow(this::forbidden);
        if (user.getRole() != UserRole.INSTRUCTOR || user.getAccountStatus() != AccountStatus.ACTIVE
                || instructors.findByIdCourseIdAndIdInstructorId(course, actor).isEmpty()) {
            throw forbidden();
        }
    }

    private void requireViewer(UUID actor, UUID course) {
        var user = users.findById(actor).orElseThrow(this::forbidden);
        if (user.getAccountStatus() == AccountStatus.ACTIVE && user.getRole() == UserRole.ADMIN) return;
        requireInstructor(actor, course);
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Course instructor access required");
    }

    private static String csvText(String value) {
        String trimmed = value.stripLeading();
        boolean formula = !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0;
        return "\"" + (formula ? "'" : "") + value.replace("\"", "\"\"") + "\"";
    }
}
