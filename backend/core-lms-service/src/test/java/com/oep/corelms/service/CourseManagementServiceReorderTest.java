package com.oep.corelms.service;

import com.oep.corelms.dto.course.LessonDtos;
import com.oep.corelms.dto.course.ModuleDtos;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonContentReferenceRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseManagementServiceReorderTest {
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

    private CourseManagementService service;
    private final UUID actorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private Course course;

    @BeforeEach
    void setUp() {
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences, courseInstructors, auditLogs, users, enrollments, objectStorage);
        course = new Course();
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        lenient().when(courses.findById(courseId)).thenReturn(Optional.of(course));
        lenient().when(courses.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));

        CourseInstructor owner = new CourseInstructor();
        owner.setCourseRole(CourseInstructorRole.OWNER);
        lenient().when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)).thenReturn(Optional.of(owner));
        User instructor = new User();
        instructor.setId(actorId);
        instructor.setRole(UserRole.INSTRUCTOR);
        lenient().when(users.findById(actorId)).thenReturn(Optional.of(instructor));
    }

    @Test
    void insertingModuleAtUsedPositionShiftsExistingModulesWithoutChangingTheirOrder() {
        CourseModule first = module(0);
        CourseModule second = module(1);
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(first, second));
        when(modules.saveAndFlush(any(CourseModule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var inserted = service.createModule(actorId, courseId,
                new ModuleDtos.CreateRequest("Inserted", null, 1, false));

        assertEquals(1, inserted.position());
        assertEquals(0, first.getPosition());
        assertEquals(2, second.getPosition());
        verify(modules, times(2)).saveAllAndFlush(anyIterable());
    }

    @Test
    void insertingLessonAtUsedPositionShiftsExistingLessonsWithoutChangingTheirOrder() {
        CourseModule parent = module(0);
        Lesson first = lesson(parent, 0);
        Lesson second = lesson(parent, 1);
        when(modules.findByIdAndCourseIdForUpdate(parent.getId(), courseId)).thenReturn(Optional.of(parent));
        when(lessons.findByModuleIdOrderByPositionAsc(parent.getId())).thenReturn(List.of(first, second));
        when(lessons.saveAndFlush(any(Lesson.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var inserted = service.createLesson(actorId, courseId, parent.getId(),
                new LessonDtos.CreateRequest("Inserted", null, 1, true));

        assertEquals(1, inserted.position());
        assertEquals(0, first.getPosition());
        assertEquals(2, second.getPosition());
        verify(lessons, times(2)).saveAllAndFlush(anyIterable());
    }

    @Test
    void reordersModulesAndPersistsContiguousPositions() {
        CourseModule first = module(0);
        CourseModule second = module(1);
        CourseModule third = module(2);
        List<CourseModule> current = List.of(first, second, third);
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(current);

        var result = service.reorderModules(actorId, courseId,
                new com.oep.corelms.dto.course.ModuleDtos.ReorderRequest(List.of(third.getId(), first.getId(), second.getId())));

        assertEquals(List.of(third.getId(), first.getId(), second.getId()), result.orderedIds());
        assertEquals(0, third.getPosition());
        assertEquals(1, first.getPosition());
        assertEquals(2, second.getPosition());
        verify(modules, times(2)).saveAllAndFlush(anyIterable());
    }

    @Test
    void rejectsModuleOrderWithOmittedIdBeforeWriting() {
        CourseModule first = module(0);
        CourseModule second = module(1);
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(first, second));

        BusinessException error = assertThrows(BusinessException.class, () -> service.reorderModules(actorId, courseId,
                new com.oep.corelms.dto.course.ModuleDtos.ReorderRequest(List.of(first.getId()))));

        assertEquals(ErrorCode.INVALID_MODULE_ORDER, error.getCode());
        verify(modules, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    void rejectsDuplicateModuleIdsBeforeWriting() {
        CourseModule first = module(0);
        CourseModule second = module(1);
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(first, second));

        BusinessException error = assertThrows(BusinessException.class, () -> service.reorderModules(actorId, courseId,
                new com.oep.corelms.dto.course.ModuleDtos.ReorderRequest(List.of(first.getId(), first.getId()))));

        assertEquals(ErrorCode.INVALID_MODULE_ORDER, error.getCode());
        verify(modules, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    void rejectsModuleIdFromAnotherCourseBeforeWriting() {
        CourseModule first = module(0);
        CourseModule second = module(1);
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(first, second));

        BusinessException error = assertThrows(BusinessException.class, () -> service.reorderModules(actorId, courseId,
                new com.oep.corelms.dto.course.ModuleDtos.ReorderRequest(List.of(first.getId(), UUID.randomUUID()))));

        assertEquals(ErrorCode.INVALID_MODULE_ORDER, error.getCode());
        verify(modules, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    void reordersLessonsAndPersistsContiguousPositions() {
        CourseModule module = module(0);
        when(modules.findByIdAndCourseIdForUpdate(module.getId(), courseId)).thenReturn(Optional.of(module));
        Lesson first = lesson(module, 0);
        Lesson second = lesson(module, 1);
        when(lessons.findByModuleIdOrderByPositionAsc(module.getId())).thenReturn(List.of(first, second));

        var result = service.reorderLessons(actorId, courseId, module.getId(),
                new com.oep.corelms.dto.course.LessonDtos.ReorderRequest(List.of(second.getId(), first.getId())));

        assertEquals(List.of(second.getId(), first.getId()), result.orderedIds());
        assertEquals(0, second.getPosition());
        assertEquals(1, first.getPosition());
        verify(lessons, times(2)).saveAllAndFlush(anyIterable());
    }

    @Test
    void rejectsLessonsFromAnotherModuleBeforeWriting() {
        CourseModule module = module(0);
        when(modules.findByIdAndCourseIdForUpdate(module.getId(), courseId)).thenReturn(Optional.of(module));
        Lesson first = lesson(module, 0);
        Lesson second = lesson(module, 1);
        when(lessons.findByModuleIdOrderByPositionAsc(module.getId())).thenReturn(List.of(first, second));

        BusinessException error = assertThrows(BusinessException.class, () -> service.reorderLessons(actorId, courseId,
                module.getId(), new com.oep.corelms.dto.course.LessonDtos.ReorderRequest(List.of(first.getId(), UUID.randomUUID()))));

        assertEquals(ErrorCode.INVALID_LESSON_ORDER, error.getCode());
        verify(lessons, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    void rejectsCallerWithoutCourseInstructorAssociation() {
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)).thenReturn(Optional.empty());

        BusinessException error = assertThrows(BusinessException.class, () -> service.reorderModules(actorId, courseId,
                new com.oep.corelms.dto.course.ModuleDtos.ReorderRequest(List.of(UUID.randomUUID()))));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
        verify(courses, never()).findByIdForUpdate(courseId);
    }

    private CourseModule module(int position) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setCourse(course);
        module.setPosition(position);
        return module;
    }

    private Lesson lesson(CourseModule module, int position) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        lesson.setPosition(position);
        return lesson;
    }
}
