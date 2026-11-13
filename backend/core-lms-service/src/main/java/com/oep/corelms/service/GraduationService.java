package com.oep.corelms.service;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GraduationService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final LessonMasteryService mastery;
    private final JdbcTemplate jdbc;

    public record Survey(@NotNull @Min(1) @Max(5) Integer csat, @NotNull @Min(0) @Max(10) Integer nps) {}
    public record Threshold(@NotNull @Min(0) @Max(100) Integer percent) {}
    public record Progress(int requiredLessons, int completedLessons, boolean courseComplete, Integer threshold,
                           BigDecimal postTest, BigDecimal massExam, BigDecimal assignment, BigDecimal finalPercent,
                           boolean surveySubmitted, boolean eligible) {}

    @Transactional(readOnly = true)
    public Progress get(UUID actor, UUID courseId) {
        return progress(actor, access(actor, courseId));
    }

    @Transactional
    public Progress submitSurvey(UUID actor, UUID courseId, Survey survey) {
        Enrollment enrollment = enrollments.lockForAssessment(courseId, actor).filter(this::eligibleAccess)
                .orElseThrow(this::forbidden);
        jdbc.update("""
                insert into course_surveys (enrollment_id, csat, nps, submitted_at) values (?, ?, ?, now())
                on conflict (enrollment_id) do nothing
                """, enrollment.getId(), survey.csat(), survey.nps());
        return progress(actor, enrollment);
    }

    @Transactional
    public Threshold updateThreshold(UUID courseId, Threshold threshold) {
        var course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khóa học"));
        course.setGraduationThreshold(threshold.percent());
        return threshold;
    }

    @Transactional(readOnly = true)
    public Threshold threshold(UUID courseId) {
        return courses.findById(courseId).map(course -> new Threshold(
                course.getGraduationThreshold() == null ? 70 : course.getGraduationThreshold()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khóa học"));
    }

    private Progress progress(UUID actor, Enrollment enrollment) {
        var course = enrollment.getCourse();
        var required = jdbc.queryForList("""
                select l.id from lessons l join course_modules m on m.id = l.module_id
                where m.course_id = ? and l.is_required
                """, UUID.class, course.getId());
        long completed = mastery.lessons(actor, course.getId()).stream().filter(item -> required.contains(item.id()))
                .filter(item -> item.unlocked() && (item.fastTrack() || (item.video() && item.documents() && item.miniQuiz())))
                .count();
        boolean courseComplete = !required.isEmpty() && completed == required.size();
        BigDecimal post = quizPercent(enrollment.getId(), course.getId(), "POST_TEST");
        BigDecimal exam = quizPercent(enrollment.getId(), course.getId(), "MASS_EXAM");
        BigDecimal assignment = jdbc.queryForObject("""
                select case when count(a.id) = count(g.id)
                    then avg(g.score * 100 / nullif(a.max_score, 0)) end
                from assignments a
                left join assignment_submissions s on s.assignment_id = a.id and s.enrollment_id = ? and s.status = 'GRADED'
                left join assignment_grades g on g.submission_id = s.id and a.max_score > 0
                where a.course_id = ?
                """, BigDecimal.class, enrollment.getId(), course.getId());
        BigDecimal finalPercent = weighted(post, exam, assignment, course.getWeightPosttest(),
                course.getWeightMassExam(), course.getWeightAssignment());
        int threshold = course.getGraduationThreshold() == null ? 70 : course.getGraduationThreshold();
        boolean survey = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from course_surveys where enrollment_id = ?)", Boolean.class, enrollment.getId()));
        return new Progress(required.size(), (int) completed, courseComplete, threshold, post, exam, assignment,
                finalPercent, survey, courseComplete && survey && finalPercent != null
                && finalPercent.compareTo(BigDecimal.valueOf(threshold)) >= 0);
    }

    private BigDecimal quizPercent(UUID enrollment, UUID course, String kind) {
        return jdbc.queryForObject("""
                with graded as (
                    select a.quiz_id, a.attempt_number, r.final_score * 100 / nullif(points.total, 0) as percent
                    from quiz_attempts a join quiz_results r on r.attempt_id = a.id
                    join quizzes z on z.id = a.quiz_id and z.course_id = ? and z.assessment_kind = ?
                    cross join lateral (select sum(cast(item.value -> 'prompt' ->> 'points' as numeric)) as total
                        from jsonb_array_elements(a.snapshot_payload -> 'items') item(value)) points
                    where a.enrollment_id = ? and a.status = 'GRADED' and points.total > 0
                ), per_quiz as (
                    select z.id, case z.grading_policy
                        when 'LATEST' then (array_agg(g.percent order by g.attempt_number desc))[1]
                        when 'AVERAGE' then avg(g.percent)
                        else max(g.percent) end as percent
                    from quizzes z left join graded g on g.quiz_id = z.id
                    where z.course_id = ? and z.assessment_kind = ?
                    group by z.id, z.grading_policy
                )
                select case when count(*) = count(percent) then avg(percent) end from per_quiz
                """, BigDecimal.class, course, kind, enrollment, course, kind);
    }

    static BigDecimal weighted(BigDecimal post, BigDecimal exam, BigDecimal assignment, Integer wp, Integer we, Integer wa) {
        if (!CoursePublishValidator.validWeights(wp, we, wa)
                || (wp > 0 && post == null) || (we > 0 && exam == null) || (wa > 0 && assignment == null)) return null;
        return component(post, wp).add(component(exam, we)).add(component(assignment, wa))
                .movePointLeft(2).setScale(2, RoundingMode.DOWN);
    }

    private static BigDecimal component(BigDecimal score, int weight) {
        return weight == 0 ? BigDecimal.ZERO : score.multiply(BigDecimal.valueOf(weight));
    }

    private Enrollment access(UUID actor, UUID course) {
        return enrollments.findByCourseIdAndStudentId(course, actor).filter(this::eligibleAccess).orElseThrow(this::forbidden);
    }

    private boolean eligibleAccess(Enrollment item) {
        return item.getStatus() == EnrollmentStatus.ACTIVE && item.getStudent().getRole() == UserRole.STUDENT
                && item.getStudent().getAccountStatus() == AccountStatus.ACTIVE
                && item.getCourse().getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED
                && (item.getAccessExpiresAt() == null || !Instant.now().isAfter(item.getAccessExpiresAt()));
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Cần quyền học còn hiệu lực để xét tốt nghiệp");
    }
}
