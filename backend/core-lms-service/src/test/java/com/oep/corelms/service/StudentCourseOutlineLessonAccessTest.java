package com.oep.corelms.service;

import com.oep.corelms.dto.student.CourseOutlineResponse;
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
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCourseOutlineLessonAccessTest {

    @Mock private CourseRepository courses;
    @Mock private CourseModuleRepository modules;
    @Mock private LessonRepository lessons;
    @Mock private LessonContentRepository lessonContents;
    @Mock private LessonProgressRepository lessonProgress;
    @Mock private LessonAccessService lessonAccess;
    @Mock private EnrollmentRepository enrollments;
    @Mock private EnrollmentAccessService enrollmentAccess;
    @Mock private CourseInstructorRepository courseInstructors;
    @Mock private StorageService storageService;
    @Mock private LessonCompletionRuleRepository completionRules;
    @Mock private QuizRepository quizzes;
    @Mock private QuizAttemptRepository quizAttempts;
    @Mock private AssignmentRepository assignments;
    @Mock private AssignmentSubmissionRepository assignmentSubmissions;
    @Mock private TuitionPaymentService tuitionPaymentService;
    @InjectMocks private StudentCourseContentService contentService;

    @Test
    void outlineHidesLockedLessonContentUsingSharedLessonScope() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Course");
        course.setLearningMode(LearningMode.SEQUENTIAL);
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setCourse(course);
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setCourse(course);
        Lesson first = lesson(module, "First");
        Lesson locked = lesson(module, "Locked");
        LessonContent firstContent = new LessonContent();
        firstContent.setId(UUID.randomUUID());
        firstContent.setContentType(ContentType.TEXT);
        firstContent.setTitle("Visible content");
        firstContent.setUploadStatus(UploadStatus.AVAILABLE);
        LessonContent deletingContent = new LessonContent();
        deletingContent.setId(UUID.randomUUID());
        deletingContent.setContentType(ContentType.TEXT);
        deletingContent.setTitle("Hidden while deleting");
        deletingContent.setUploadStatus(UploadStatus.DELETING);

        when(enrollmentAccess.requireReadableEnrollment(courseId, studentId)).thenReturn(enrollment);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(lessonProgress.findByEnrollmentId(enrollment.getId())).thenReturn(List.of());
        when(lessonAccess.readableLessonIds(enrollment)).thenReturn(List.of(first.getId()));
        when(modules.findByCourse_IdOrderByPositionAsc(courseId)).thenReturn(List.of(module));
        when(lessons.findByModule_IdOrderByPositionAsc(module.getId())).thenReturn(List.of(first, locked));
        when(lessonContents.findByLesson_IdOrderByPositionAsc(first.getId()))
                .thenReturn(List.of(firstContent, deletingContent));

        CourseOutlineResponse result = contentService.getOutline(courseId, studentId);

        assertEquals(2, result.modules().getFirst().lessons().size());
        CourseOutlineResponse.OutlineLesson firstResult = result.modules().getFirst().lessons().getFirst();
        CourseOutlineResponse.OutlineLesson lockedResult = result.modules().getFirst().lessons().get(1);
        assertFalse(firstResult.locked());
        assertEquals(firstContent.getId(), firstResult.contents().getFirst().id());
        assertEquals(1, firstResult.contents().size());
        assertTrue(lockedResult.locked());
        assertTrue(lockedResult.contents().isEmpty());
        verify(lessonContents, never()).findByLesson_IdOrderByPositionAsc(locked.getId());
    }

    @Test
    void protectedContentDeniesASequentiallyLockedLesson() {
        UUID studentId = UUID.randomUUID();
        Course course = new Course();
        course.setId(UUID.randomUUID());
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson locked = lesson(module, "Locked");
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(locked);
        Enrollment enrollment = new Enrollment();
        enrollment.setCourse(course);

        when(lessonContents.findById(content.getId())).thenReturn(Optional.of(content));
        when(enrollmentAccess.requireReadableEnrollment(course.getId(), studentId)).thenReturn(enrollment);
        doThrow(new BusinessException(ErrorCode.COMPLETION_RULE_NOT_MET, "Locked lesson"))
                .when(lessonAccess).requireUnlocked(locked, enrollment);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> contentService.authorizeContent(content.getId(), studentId, "STUDENT"));
        assertEquals(ErrorCode.COMPLETION_RULE_NOT_MET, denied.getCode());
    }

    @Test
    void protectedContentDeniesUnavailableMaterialToStudent() {
        UUID studentId = UUID.randomUUID();
        Course course = new Course();
        course.setId(UUID.randomUUID());
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = lesson(module, "Visible lesson");
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setUploadStatus(UploadStatus.DELETING);
        Enrollment enrollment = new Enrollment();
        enrollment.setCourse(course);
        when(lessonContents.findById(content.getId())).thenReturn(Optional.of(content));
        when(enrollmentAccess.requireReadableEnrollment(course.getId(), studentId)).thenReturn(enrollment);

        BusinessException denied = assertThrows(BusinessException.class,
                () -> contentService.authorizeContent(content.getId(), studentId, "STUDENT"));
        assertEquals(ErrorCode.NOT_FOUND, denied.getCode());
    }

    private static Lesson lesson(CourseModule module, String title) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        lesson.setTitle(title);
        return lesson;
    }
}
