package com.oep.corelms.service;

import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonContentReferenceRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonDeletionTest {
    @Mock private CourseRepository courses;
    @Mock private CourseModuleRepository modules;
    @Mock private LessonRepository lessons;
    @Mock private LessonContentRepository contents;
    @Mock private LessonContentReferenceRepository contentReferences;
    @Mock private CourseInstructorRepository courseInstructors;
    @Mock private AuditLogRepository auditLogs;
    @Mock private UserRepository users;
    @Mock private EnrollmentRepository enrollments;
    @Mock private ObjectStorageService objectStorage;

    private final UUID actorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID moduleId = UUID.randomUUID();
    private final UUID lessonId = UUID.randomUUID();
    private CourseManagementService service;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences,
                courseInstructors, auditLogs, users, enrollments, objectStorage);

        Course course = new Course();
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));

        CourseInstructor owner = new CourseInstructor();
        owner.setCourseRole(CourseInstructorRole.OWNER);
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)).thenReturn(Optional.of(owner));

        CourseModule module = new CourseModule();
        module.setId(moduleId);
        module.setCourse(course);
        when(modules.findByIdAndCourseId(moduleId, courseId)).thenReturn(Optional.of(module));

        lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setModule(module);
        when(lessons.findByIdAndModuleIdForUpdate(lessonId, moduleId)).thenReturn(Optional.of(lesson));
    }

    @Test
    void deletesLessonWhenItHasNoReferences() {
        when(lessons.hasDeletionReferences(lessonId)).thenReturn(false);

        var result = service.deleteLesson(actorId, courseId, moduleId, lessonId);

        assertTrue(result.success());
        assertEquals(lessonId, result.deletedId());
        verify(lessons).delete(lesson);
    }

    @Test
    void rejectsLessonWithReferencesAndPreservesIt() {
        when(lessons.hasDeletionReferences(lessonId)).thenReturn(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteLesson(actorId, courseId, moduleId, lessonId));

        assertEquals(ErrorCode.LESSON_NOT_EMPTY, error.getCode());
        verify(lessons, never()).delete(lesson);
    }
}
