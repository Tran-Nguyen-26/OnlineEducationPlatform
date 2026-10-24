package com.oep.corelms.service;

import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.AuditLogRepository;
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
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseOutlineServiceTest {
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
    private final UUID courseId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private Course course;

    @BeforeEach
    void setUp() {
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences, courseInstructors, auditLogs, users, enrollments, objectStorage);
        course = new Course();
        course.setId(courseId);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
    }

    @Test
    void returnsMetadataOnlyOutlineForPublicPublishedCourseWithoutAuthentication() {
        course.setVisibility(CourseVisibility.PUBLIC);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        CourseModule module = module(0, "Introduction");
        Lesson lesson = lesson(module, 0, "Welcome");
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(module));
        when(lessons.findAllForCourseOutline(courseId)).thenReturn(List.of(lesson));

        var result = service.getCourseOutline(null, courseId);

        assertEquals(courseId, result.courseId());
        assertEquals(1, result.modules().size());
        assertEquals(module.getId(), result.modules().getFirst().module().id());
        assertEquals(lesson.getId(), result.modules().getFirst().lessons().getFirst().id());
        verify(modules).findByCourseIdOrderByPositionAsc(courseId);
        verify(lessons).findAllForCourseOutline(courseId);
        verify(lessons, never()).findByModuleIdOrderByPositionAsc(module.getId());
    }

    @Test
    void buildsLargeOutlineWithOneBulkLessonLookup() {
        course.setVisibility(CourseVisibility.PUBLIC);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);

        List<CourseModule> courseModules = new java.util.ArrayList<>();
        List<Lesson> courseLessons = new java.util.ArrayList<>();
        for (int modulePosition = 0; modulePosition < 10; modulePosition++) {
            CourseModule module = module(modulePosition, "Module " + modulePosition);
            courseModules.add(module);
            for (int lessonPosition = 0; lessonPosition < 10; lessonPosition++) {
                courseLessons.add(lesson(module, lessonPosition, "Lesson " + modulePosition + "." + lessonPosition));
            }
        }
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(courseModules);
        when(lessons.findAllForCourseOutline(courseId)).thenReturn(courseLessons);

        var outline = service.getCourseOutline(null, courseId);

        assertEquals(10, outline.modules().size());
        assertEquals(100, outline.modules().stream().mapToInt(module -> module.lessons().size()).sum());
        assertEquals("Lesson 9.9", outline.modules().get(9).lessons().get(9).title());
        verify(modules, times(1)).findByCourseIdOrderByPositionAsc(courseId);
        verify(lessons, times(1)).findAllForCourseOutline(courseId);
        verify(lessons, never()).findByModuleIdOrderByPositionAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deniesAnonymousOutlineForDraftOrPrivateCourse() {
        course.setVisibility(CourseVisibility.PUBLIC);
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);

        BusinessException draftError = assertThrows(BusinessException.class,
                () -> service.getCourseOutline(null, courseId));
        assertEquals(ErrorCode.FORBIDDEN, draftError.getCode());

        course.setVisibility(CourseVisibility.PRIVATE);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        BusinessException privateError = assertThrows(BusinessException.class,
                () -> service.getCourseOutline(null, courseId));
        assertEquals(ErrorCode.FORBIDDEN, privateError.getCode());
    }

    @Test
    void allowsCourseOwnerToReadArchivedOutline() {
        course.setVisibility(CourseVisibility.PRIVATE);
        course.setLifecycleStatus(CourseLifecycleStatus.ARCHIVED);
        CourseInstructor owner = new CourseInstructor();
        owner.setCourseRole(CourseInstructorRole.OWNER);
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, ownerId)).thenReturn(Optional.of(owner));
        User ownerUser = new User();
        ownerUser.setId(ownerId);
        ownerUser.setRole(UserRole.INSTRUCTOR);
        when(users.findById(ownerId)).thenReturn(Optional.of(ownerUser));
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of());
        when(lessons.findAllForCourseOutline(courseId)).thenReturn(List.of());

        var result = service.getCourseOutline(ownerId, courseId);

        assertEquals(courseId, result.courseId());
        assertEquals(List.of(), result.modules());
    }

    @Test
    void allowsActiveStudentToReadPrivateOutlineAndModules() {
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(UserRole.STUDENT);
        when(users.findById(studentId)).thenReturn(Optional.of(student));
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, studentId)).thenReturn(Optional.empty());
        when(enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.ACTIVE)).thenReturn(true);
        course.setVisibility(CourseVisibility.PRIVATE);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        CourseModule module = module(0, "Private module");
        when(modules.findByCourseIdOrderByPositionAsc(courseId)).thenReturn(List.of(module));
        when(lessons.findAllForCourseOutline(courseId)).thenReturn(List.of());
        when(modules.findByCourseIdOrderByPositionAsc(courseId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(module), PageRequest.of(0, 20), 1));

        var outline = service.getCourseOutline(studentId, courseId);
        var modulePage = service.listModules(studentId, courseId, 1, 20);

        assertEquals(1, outline.modules().size());
        assertEquals(1, modulePage.items().size());
    }

    @Test
    void deniesStudentWithoutActiveEnrollmentIncludingCompletedEnrollment() {
        UUID studentId = UUID.randomUUID();
        User student = new User();
        student.setId(studentId);
        student.setRole(UserRole.STUDENT);
        when(users.findById(studentId)).thenReturn(Optional.of(student));
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, studentId)).thenReturn(Optional.empty());
        when(enrollments.existsByCourseIdAndStudentIdAndStatus(courseId, studentId, EnrollmentStatus.ACTIVE)).thenReturn(false);
        course.setVisibility(CourseVisibility.PRIVATE);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getCourseOutline(studentId, courseId));

        assertEquals(ErrorCode.FORBIDDEN, error.getCode());
    }

    private CourseModule module(int position, String title) {
        CourseModule module = new CourseModule();
        module.setId(UUID.randomUUID());
        module.setCourse(course);
        module.setTitle(title);
        module.setPosition(position);
        return module;
    }

    private Lesson lesson(CourseModule module, int position, String title) {
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        lesson.setTitle(title);
        lesson.setPosition(position);
        return lesson;
    }
}
