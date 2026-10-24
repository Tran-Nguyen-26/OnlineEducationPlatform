package com.oep.corelms.service;

import com.oep.corelms.dto.course.CourseDtos;
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
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.UserRole;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCreationTest {
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

    private final UUID instructorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private User instructor;
    private CourseManagementService service;

    @BeforeEach
    void setUp() {
        instructor = new User();
        instructor.setId(instructorId);
        instructor.setRole(UserRole.INSTRUCTOR);
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences,
                courseInstructors, auditLogs, users, enrollments, objectStorage);
        when(users.findById(instructorId)).thenReturn(Optional.of(instructor));
    }

    @Test
    void createsCourseOwnerAndDefaultModuleTogether() {
        when(courses.saveAndFlush(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(courseId);
            return course;
        });

        var response = service.createCourse(instructorId, new CourseDtos.CreateCourseRequest(
                "Cloud Basics", null, CourseVisibility.PUBLIC, null, LearningMode.FREE_NAVIGATION, null, null));

        assertEquals(courseId, response.id());
        assertEquals(CourseLifecycleStatus.DRAFT, response.lifecycleStatus());
        verify(courseInstructors).save(argThat((CourseInstructor owner) ->
                owner.getId().getCourseId().equals(courseId)
                        && owner.getId().getInstructorId().equals(instructorId)
                        && owner.getCourseRole() == CourseInstructorRole.OWNER));
        verify(modules).save(argThat((CourseModule module) ->
                module.getCourse().getId().equals(courseId)
                        && module.getTitle().equals("Module 1: Introduction")
                        && module.getPosition() == 0
                        && module.isDefaultModule()));
    }

    @Test
    void rejectsNonInstructorWithoutCreatingCourseOrDefaultModule() {
        instructor.setRole(UserRole.STUDENT);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCourse(instructorId,
                new CourseDtos.CreateCourseRequest("Cloud Basics", null, CourseVisibility.PUBLIC, null,
                        LearningMode.FREE_NAVIGATION, null, null)));

        assertEquals(ErrorCode.INSTRUCTOR_ROLE_REQUIRED, error.getCode());
        verify(courses, never()).saveAndFlush(any(Course.class));
        verify(modules, never()).save(any(CourseModule.class));
    }
}
