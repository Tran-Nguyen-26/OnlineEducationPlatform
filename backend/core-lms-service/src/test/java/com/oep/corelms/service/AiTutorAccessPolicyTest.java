package com.oep.corelms.service;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.QuizAttemptRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.QuizAttemptStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTutorAccessPolicyTest {

    private final UUID studentId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private CourseRepository courses;
    private EnrollmentRepository enrollments;
    private QuizAttemptRepository attempts;
    private LessonAccessService lessonAccess;
    private AiTutorAccessPolicy policy;
    private Course course;
    private Enrollment enrollment;

    @BeforeEach
    void setUp() {
        courses = mock(CourseRepository.class);
        enrollments = mock(EnrollmentRepository.class);
        attempts = mock(QuizAttemptRepository.class);
        lessonAccess = mock(LessonAccessService.class);
        policy = new AiTutorAccessPolicy(courses, new EnrollmentAccessService(enrollments), attempts, lessonAccess);
        course = new Course();
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        enrollment = new Enrollment();
        enrollment.setId(enrollmentId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
    }

    @Test
    void rejectsInstructorAndAdminBeforeReadingCourseOrEnrollment() {
        for (String role : new String[] {"INSTRUCTOR", "ADMIN"}) {
            assertError(ErrorCode.FORBIDDEN, () -> policy.requireCourseAccess(studentId, role, courseId));
        }
        verify(courses, never()).findById(courseId);
    }

    @Test
    void rejectsNonPublishedAndUpcomingCourses() {
        for (CourseLifecycleStatus status : new CourseLifecycleStatus[] {
                CourseLifecycleStatus.DRAFT, CourseLifecycleStatus.ARCHIVED, CourseLifecycleStatus.DISABLED}) {
            course.setLifecycleStatus(status);
            assertError(ErrorCode.FORBIDDEN, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
        }
        verify(enrollments, never()).findByCourseIdAndStudentId(courseId, studentId);

        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        course.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        assertError(ErrorCode.COURSE_NOT_OPEN, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
        verify(attempts, never()).existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollmentId, courseId, QuizAttemptStatus.IN_PROGRESS);
    }

    @Test
    void rejectsMissingAndCompletedEnrollment() {
        assertError(ErrorCode.NOT_ENROLLED, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        for (EnrollmentStatus status : new EnrollmentStatus[] {
                EnrollmentStatus.COMPLETED, EnrollmentStatus.WITHDRAWN, EnrollmentStatus.REMOVED}) {
            enrollment.setStatus(status);
            assertError(ErrorCode.NOT_ENROLLED, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
        }
        verify(attempts, never()).existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollmentId, courseId, QuizAttemptStatus.IN_PROGRESS);
    }

    @Test
    void returnsNotFoundForAnUnknownCourseWithoutReadingEnrollment() {
        when(courses.findById(courseId)).thenReturn(Optional.empty());
        assertError(ErrorCode.NOT_FOUND, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
        verify(enrollments, never()).findByCourseIdAndStudentId(courseId, studentId);
    }

    @Test
    void blocksAnActiveQuizInTheSameCourse() {
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        when(attempts.existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollmentId, courseId, QuizAttemptStatus.IN_PROGRESS)).thenReturn(true);
        assertError(ErrorCode.QUIZ_LOCKED, () -> policy.requireCourseAccess(studentId, "STUDENT", courseId));
    }

    @Test
    void permitsActiveStudentWithoutAnActiveQuiz() {
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        assertSame(enrollment, policy.requireCourseAccess(studentId, "STUDENT", courseId));
        verify(attempts).existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollmentId, courseId, QuizAttemptStatus.IN_PROGRESS);
    }

    @Test
    void resolvesOnlyReadableLessonsAfterCurrentAccessChecks() {
        UUID readableLesson = UUID.randomUUID();
        when(enrollments.findByCourseIdAndStudentId(courseId, studentId)).thenReturn(Optional.of(enrollment));
        when(lessonAccess.readableLessonIds(enrollment)).thenReturn(List.of(readableLesson));

        AiTutorAccessPolicy.AuthorizedLessonScope scope =
                policy.requireAuthorizedLessonScope(studentId, "STUDENT", courseId);

        assertEquals(studentId, scope.userId());
        assertEquals(courseId, scope.courseId());
        assertEquals(List.of(readableLesson), scope.lessonIds());
        verify(attempts).existsByEnrollment_IdAndQuiz_Course_IdAndStatus(
                enrollmentId, courseId, QuizAttemptStatus.IN_PROGRESS);
    }

    private static void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
