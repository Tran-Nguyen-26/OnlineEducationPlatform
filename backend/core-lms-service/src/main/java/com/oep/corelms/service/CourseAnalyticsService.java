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

@Service
@RequiredArgsConstructor
public class CourseAnalyticsService {
    private final JdbcTemplate jdbc;
    private final CourseInstructorRepository instructors;
    private final UserRepository users;
    private final NotificationPublisher notifications;

    public enum IssueCategory { CONTENT, RUBRIC }

    public record LessonMetrics(UUID id, String title, long started, long completed) {}
    public record Analytics(long learners, long completed, List<LessonMetrics> lessons) {}

    @Transactional(readOnly = true)
    public Analytics analytics(UUID actor, UUID course) {
        requireInstructor(actor, course);
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
        return new Analytics(counts[0], counts[1], lessons);
    }

    public static String csv(Analytics metrics) {
        StringBuilder report = new StringBuilder("\uFEFFlesson_id,lesson,learners,started,completed,completion_percent\r\n");
        for (LessonMetrics lesson : metrics.lessons()) {
            String rate = metrics.learners() == 0 ? "" : String.format(Locale.ROOT, "%.2f",
                    100.0 * lesson.completed() / metrics.learners());
            report.append(lesson.id()).append(',').append(csvText(lesson.title())).append(',')
                    .append(metrics.learners()).append(',').append(lesson.started()).append(',')
                    .append(lesson.completed()).append(',').append(rate).append("\r\n");
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

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Course instructor access required");
    }

    private static String csvText(String value) {
        String trimmed = value.stripLeading();
        boolean formula = !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0;
        return "\"" + (formula ? "'" : "") + value.replace("\"", "\"\"") + "\"";
    }
}
