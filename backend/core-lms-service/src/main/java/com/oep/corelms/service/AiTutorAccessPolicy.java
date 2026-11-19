package com.oep.corelms.service;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CoursePhase;
import com.oep.coremodel.enums.QuizAttemptStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Authoritative student/course/quiz guard for every future public M7 request. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorAccessPolicy {

    private final CourseRepository courses;
    private final EnrollmentAccessService enrollmentAccess;
    private final QuizAttemptRepository quizAttempts;

    /**
     * Uses a primary-routed transaction: a replica can lag a newly started quiz or revoked enrollment.
     * Lesson-level visibility is a separate check before retrieval; a passed course check does not
     * authorize evidence from sequential lessons that have not opened for this student.
     */
    @Transactional
    public Enrollment requireCourseAccess(UUID userId, String role, UUID courseId) {
        if (!"STUDENT".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "AI Tutor is available to students only");
        }
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course is not published");
        }
        Enrollment enrollment = enrollmentAccess.requireActiveEnrollment(courseId, userId);
        if (course.currentPhase() == CoursePhase.UPCOMING) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Course learning content is not open yet");
        }
        if (quizAttempts.existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollment.getId(), courseId, QuizAttemptStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.QUIZ_LOCKED, "AI Tutor is unavailable during a course quiz");
        }
        return enrollment;
    }
}
