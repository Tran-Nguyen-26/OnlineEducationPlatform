package com.oep.corelms.service;

import com.oep.corelms.dto.student.CourseOutlineResponse;
import com.oep.corelms.dto.student.CourseOutlineResponse.OutlineContent;
import com.oep.corelms.dto.student.CourseOutlineResponse.OutlineLesson;
import com.oep.corelms.dto.student.CourseOutlineResponse.OutlineModule;
import com.oep.corelms.dto.student.MyProgressResponse;
import com.oep.corelms.dto.student.ViewUrlResponse;
import com.oep.corelms.repository.AssignmentRepository;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonCompletionRuleRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonProgressRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.corelms.repository.QuizRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonCompletionRule;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.LessonProgress;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The full module/lesson/content tree is only readable while the student is actively enrolled. */
@Service
@RequiredArgsConstructor
public class StudentCourseContentService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonContentRepository lessonContents;
    private final LessonProgressRepository lessonProgress;
    private final EnrollmentRepository enrollments;
    private final EnrollmentAccessService enrollmentAccess;
    private final CourseInstructorRepository courseInstructors;
    private final StorageService storageService;
    private final LessonCompletionRuleRepository completionRules;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository quizAttempts;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository assignmentSubmissions;
    private final TuitionPaymentService tuitionPaymentService;

    @Transactional(readOnly = true)
    public CourseOutlineResponse getOutline(UUID courseId, UUID studentId) {
        Enrollment enrollment = enrollmentAccess.requireActiveEnrollment(courseId, studentId);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        Map<UUID, LessonProgressStatus> progressByLesson = lessonProgress.findByEnrollmentId(enrollment.getId()).stream()
                .collect(Collectors.toMap(p -> p.getLesson().getId(), LessonProgress::getStatus));

        List<OutlineModule> moduleDtos = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .map(module -> new OutlineModule(
                        module.getId(),
                        module.getTitle(),
                        module.getDescription(),
                        module.getPosition(),
                        lessons.findByModule_IdOrderByPositionAsc(module.getId()).stream()
                                .map(lesson -> toOutlineLesson(lesson, progressByLesson))
                                .toList()))
                .toList();

        return new CourseOutlineResponse(courseId, course.getTitle(), moduleDtos);
    }

    private OutlineLesson toOutlineLesson(Lesson lesson, Map<UUID, LessonProgressStatus> progressByLesson) {
        List<OutlineContent> contentDtos = lessonContents.findByLesson_IdOrderByPositionAsc(lesson.getId()).stream()
                .map(c -> new OutlineContent(c.getId(), c.getContentType(), c.getTitle(), c.getTextContent(), c.getExternalUrl(),
                        c.getFileName(), c.getMimeType(), c.getDurationSeconds(), c.getUploadStatus()))
                .toList();
        LessonProgressStatus status = progressByLesson.getOrDefault(lesson.getId(), LessonProgressStatus.NOT_STARTED);
        return new OutlineLesson(lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getPosition(),
                lesson.isRequired(), status, contentDtos);
    }

    /** Either an actively-enrolled student or an assigned instructor/admin may view the file; others get 403. */
    @Transactional(readOnly = true)
    public ViewUrlResponse getContentViewUrl(UUID contentId, UUID userId, String role) {
        LessonContent content = lessonContents.findById(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Content not found"));
        UUID courseId = content.getLesson().getModule().getCourse().getId();

        if ("INSTRUCTOR".equals(role)) {
            if (!courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "You are not assigned to this course");
            }
        } else if (!"ADMIN".equals(role)) {
            enrollmentAccess.requireActiveEnrollment(courseId, userId);
        }

        if (content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "This content has no file to view");
        }
        return new ViewUrlResponse(storageService.presignView(content.getObjectKey()), storageService.viewUrlTtlSeconds());
    }

    /** One row per enrollment (any status), with lesson-completion counts aggregated across all its modules. */
    @Transactional(readOnly = true)
    public List<MyProgressResponse> myProgress(UUID studentId) {
        return enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId).stream()
                .map(this::toProgressResponse)
                .toList();
    }

    private MyProgressResponse toProgressResponse(Enrollment enrollment) {
        UUID courseId = enrollment.getCourse().getId();
        int totalLessons = modules.findByCourse_IdOrderByPositionAsc(courseId).stream()
                .mapToInt(m -> (int) lessons.countByModule_Id(m.getId()))
                .sum();
        long completedLessons = lessonProgress.findByEnrollmentId(enrollment.getId()).stream()
                .filter(p -> p.getStatus() == LessonProgressStatus.COMPLETED)
                .count();
        double percent = totalLessons == 0 ? 0.0 : Math.round(completedLessons * 1000.0 / totalLessons) / 10.0;

        java.math.BigDecimal feeAmount = enrollment.getCourse().getFeeAmount();
        java.math.BigDecimal totalPaid = tuitionPaymentService.totalPaid(enrollment.getId());
        return new MyProgressResponse(enrollment.getId(), courseId, enrollment.getCourse().getTitle(),
                enrollment.getCourse().getSubject().getName(), enrollment.getStatus(), totalLessons,
                (int) completedLessons, percent, feeAmount, totalPaid, tuitionPaymentService.status(feeAmount, totalPaid));
    }

    @Transactional
    public void updateLessonProgress(UUID lessonId, UUID studentId, LessonProgressStatus status) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
        UUID courseId = lesson.getModule().getCourse().getId();
        Enrollment enrollment = enrollmentAccess.requireActiveEnrollment(courseId, studentId);

        LessonProgress progress = lessonProgress.findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .orElseGet(() -> {
                    LessonProgress p = new LessonProgress();
                    p.setEnrollment(enrollment);
                    p.setLesson(lesson);
                    return p;
                });
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(Instant.now());
        }
        int elapsedSeconds = (int) Duration.between(progress.getStartedAt(), Instant.now()).getSeconds();
        progress.setTimeSpentSeconds(Math.max(progress.getTimeSpentSeconds(), elapsedSeconds));

        if (status == LessonProgressStatus.COMPLETED) {
            completionRules.findByLesson_Id(lessonId).ifPresent(rule -> enforceCompletionRule(rule, enrollment, progress));
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(Instant.now());
            }
        }
        progress.setStatus(status);
        progress.setLastAccessedAt(Instant.now());
        lessonProgress.save(progress);
    }

    /**
     * minVideoPercentage is intentionally not enforced here: the content viewer has no in-app video player
     * (files just open in a new tab), so there is no watch-time telemetry to check it against yet (GAPS.md G2).
     */
    private void enforceCompletionRule(LessonCompletionRule rule, Enrollment enrollment, LessonProgress progress) {
        if (progress.getTimeSpentSeconds() < rule.getMinimumTimeSeconds()) {
            throw new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET,
                    "Bạn cần dành ít nhất " + rule.getMinimumTimeSeconds() + " giây cho bài học này trước khi đánh dấu hoàn thành");
        }
        UUID lessonId = progress.getLesson().getId();
        if (rule.isRequireQuiz()) {
            boolean attempted = quizzes.findByLesson_Id(lessonId).stream()
                    .anyMatch(quiz -> quizAttempts.existsByQuiz_IdAndEnrollment_Id(quiz.getId(), enrollment.getId()));
            if (!attempted) {
                throw new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET, "Bạn cần làm quiz của bài học này trước");
            }
        }
        if (rule.isRequireAssignment()) {
            boolean submitted = assignments.findByLesson_Id(lessonId).stream()
                    .anyMatch(a -> assignmentSubmissions.existsByAssignment_IdAndEnrollment_Id(a.getId(), enrollment.getId()));
            if (!submitted) {
                throw new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET, "Bạn cần nộp bài tập của bài học này trước");
            }
        }
    }
}
