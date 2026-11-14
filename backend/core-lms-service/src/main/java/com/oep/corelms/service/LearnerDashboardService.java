package com.oep.corelms.service;

import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearnerDashboardService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public record Summary(long activeCourses, long completedCourses, long completedLessons,
                          long studyHours, List<CourseProgress> courses, List<Activity> recentActivity) {}
    public record CourseProgress(UUID courseId, String courseTitle, long completedLessons,
                                 long totalLessons, Instant lastAccessedAt, String accessState) {}
    public record Activity(UUID courseId, String courseTitle, String lessonTitle,
                           String status, Instant occurredAt) {}

    @Transactional(readOnly = true)
    public Summary summary(UUID learnerId) {
        var learner = users.findById(learnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản không khả dụng"));
        if (learner.getAccountStatus() != AccountStatus.ACTIVE || learner.getRole() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ học viên đang hoạt động được xem tổng quan học tập");
        }

        long[] totals = jdbc.queryForObject("""
                select count(*) filter (where e.status = 'ACTIVE'),
                       count(*) filter (where e.status = 'COMPLETED'),
                       count(*) filter (where p.status = 'COMPLETED'),
                       coalesce(sum(p.time_spent_seconds), 0)
                from enrollments e
                left join lesson_progress p on p.enrollment_id = e.id
                where e.student_id = ? and e.status in ('ACTIVE', 'COMPLETED')
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)}, learnerId);

        List<CourseProgress> courses = jdbc.query("""
                select c.id, c.title,
                       count(*) filter (where p.status = 'COMPLETED'),
                       (select count(*) from course_modules m join lessons all_lessons on all_lessons.module_id = m.id
                         where m.course_id = c.id),
                       max(p.last_accessed_at),
                       case when e.access_expires_at is not null and e.access_expires_at < now()
                            then 'ARCHIVED_STATIC' else 'ACTIVE' end
                from enrollments e
                join courses c on c.id = e.course_id
                left join lesson_progress p on p.enrollment_id = e.id
                where e.student_id = ? and e.status in ('ACTIVE', 'COMPLETED')
                group by c.id, c.title, e.access_expires_at, e.enrolled_at
                order by max(p.last_accessed_at) desc nulls last, e.enrolled_at desc
                limit 6
                """, (rs, row) -> new CourseProgress(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getLong(3), rs.getLong(4), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant(),
                rs.getString(6)), learnerId);

        List<Activity> activity = jdbc.query("""
                select c.id, c.title, l.title, p.status, p.last_accessed_at
                from lesson_progress p
                join enrollments e on e.id = p.enrollment_id
                join lessons l on l.id = p.lesson_id
                join course_modules m on m.id = l.module_id
                join courses c on c.id = m.course_id
                where e.student_id = ? and e.status in ('ACTIVE', 'COMPLETED')
                  and p.status in ('IN_PROGRESS', 'COMPLETED')
                order by p.last_accessed_at desc, p.id desc
                limit 8
                """, (rs, row) -> new Activity(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()), learnerId);

        return new Summary(totals[0], totals[1], totals[2], Math.round(totals[3] / 3600.0), courses, activity);
    }
}
