package com.oep.corelms.service;

import com.oep.corelms.dto.student.CourseCompletionResponse;
import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonProgressRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Self-service course completion. Criteria: every required lesson completed, every (unlocked) quiz attempted at least
 * once and every assignment submitted at least once. Admin can still complete an enrollment manually regardless.
 */
@Service
@RequiredArgsConstructor
public class CourseCompletionService {

    private final EnrollmentRepository enrollments;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonProgressRepository lessonProgress;
    private final FinalGradeService finalGrades;
    private final CertificateService certificateService;
    private final WaitlistService waitlistService;

    @Transactional(readOnly = true)
    public CourseCompletionResponse status(UUID courseId, UUID studentId) {
        return evaluate(courseId, requireEnrollment(courseId, studentId));
    }

    @Transactional
    public CourseCompletionResponse complete(UUID courseId, UUID studentId) {
        Enrollment e = requireEnrollment(courseId, studentId);
        if (e.getStatus() == EnrollmentStatus.COMPLETED) {
            return evaluate(courseId, e);
        }
        if (e.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_ENROLLED, "Enrollment is not active");
        }
        CourseCompletionResponse check = evaluate(courseId, e);
        if (!check.eligible()) {
            throw new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET,
                    check.passed() != null && !check.passed() && check.pendingGrading() == 0 && check.attemptedQuizzes() == check.quizzes()
                            && check.submittedAssignments() == check.assignments()
                            ? "Điểm tổng kết chưa đạt mức tối thiểu (" + check.passingPercent() + "%)"
                            : "Bạn chưa đáp ứng đủ yêu cầu hoàn thành khóa học (bài học bắt buộc, quiz, bài tập, chấm điểm)");
        }
        e.setStatus(EnrollmentStatus.COMPLETED);
        e.setCompletedAt(Instant.now());
        Enrollment saved = enrollments.save(e);
        certificateService.issueIfAbsent(saved);
        waitlistService.promoteNext(courseId);
        return evaluate(courseId, saved);
    }

    private Enrollment requireEnrollment(UUID courseId, UUID studentId) {
        return enrollments.findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ENROLLED, "Not enrolled in this course"));
    }

    private CourseCompletionResponse evaluate(UUID courseId, Enrollment e) {
        List<Lesson> required = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .flatMap(m -> lessons.findByModule_IdOrderByPositionAsc(m.getId()).stream())
                .filter(Lesson::isRequired).toList();
        Set<UUID> done = lessonProgress.findByEnrollmentId(e.getId()).stream()
                .filter(p -> p.getStatus() == LessonProgressStatus.COMPLETED)
                .map(p -> p.getLesson().getId()).collect(Collectors.toSet());
        int doneRequired = (int) required.stream().filter(l -> done.contains(l.getId())).count();

        var course = e.getCourse();
        FinalGradeService.Standing st = finalGrades.standing(course, e);
        boolean completed = e.getStatus() == EnrollmentStatus.COMPLETED;
        boolean passOk = course.getPassingPercent() == null || Boolean.TRUE.equals(st.passed());
        boolean eligible = doneRequired == required.size() && !required.isEmpty()
                && st.quizzesAttempted() == st.quizzesTotal() && st.assignmentsSubmitted() == st.assignmentsTotal()
                && st.pendingCount() == 0 && passOk;
        return new CourseCompletionResponse(e.getStatus().name(), required.size(), doneRequired, st.quizzesTotal(), st.quizzesAttempted(),
                st.assignmentsTotal(), st.assignmentsSubmitted(), st.quizPercent(), st.assignmentPercent(), st.finalPercent(),
                course.getPassingPercent(), st.passed(), st.pendingCount(), st.waivedCount(), course.currentPhase().name(), eligible, completed);
    }
}
