package com.oep.corelms.service;

import com.oep.corelms.dto.course.CourseDtos;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonContentReferenceRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseLifecycleAndInstructorManagementTest {
    @Mock private CourseRepository courses;
    @Mock private CourseModuleRepository modules;
    @Mock private LessonRepository lessons;
    @Mock private LessonContentRepository contents;
    @Mock private LessonContentReferenceRepository contentReferences;
    @Mock private CourseInstructorRepository courseInstructors;
    @Mock private UserRepository users;

    private CourseManagementService service;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private Course course;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new CourseManagementService(courses, modules, lessons, contents, contentReferences, courseInstructors, users);
        course = new Course();
        course.setId(courseId);
        course.setTitle("Test course");
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        owner = user(ownerId, UserRole.INSTRUCTOR);
        lenient().when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        course.setCreatedBy(owner);

        CourseInstructor ownerLink = new CourseInstructor();
        ownerLink.setId(new CourseInstructorId(courseId, ownerId));
        ownerLink.setCourseRole(CourseInstructorRole.OWNER);
        lenient().when(courses.findByIdForUpdate(courseId)).thenReturn(Optional.of(course));
        lenient().when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, ownerId)).thenReturn(Optional.of(ownerLink));
        lenient().when(courses.saveAndFlush(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesReadyDraftCourse() {
        when(lessons.existsInCourse(courseId)).thenReturn(true);
        when(contents.existsAvailableInCourse(courseId, UploadStatus.AVAILABLE)).thenReturn(true);

        CourseDtos.CourseResponse result = service.publishCourse(ownerId, courseId);

        assertEquals(CourseLifecycleStatus.PUBLISHED, result.lifecycleStatus());
        verify(contents).existsAvailableInCourse(courseId, UploadStatus.AVAILABLE);
    }

    @Test
    void rejectsPublishingCourseWithoutLessonOrAvailableContent() {
        when(lessons.existsInCourse(courseId)).thenReturn(true);
        when(contents.existsAvailableInCourse(courseId, UploadStatus.AVAILABLE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.publishCourse(ownerId, courseId));

        assertEquals(ErrorCode.COURSE_NOT_READY, error.getCode());
        verify(courses, never()).saveAndFlush(any(Course.class));
    }

    @Test
    void rejectsPublishingByCoInstructor() {
        UUID coInstructorId = UUID.randomUUID();
        CourseInstructor link = new CourseInstructor();
        link.setCourseRole(CourseInstructorRole.CO_INSTRUCTOR);
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, coInstructorId)).thenReturn(Optional.of(link));

        BusinessException error = assertThrows(BusinessException.class, () -> service.publishCourse(coInstructorId, courseId));

        assertEquals(ErrorCode.COURSE_OWNER_REQUIRED, error.getCode());
    }

    @Test
    void unpublishesPublishedCourseAndRejectsInvalidTransition() {
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        assertEquals(CourseLifecycleStatus.DRAFT, service.unpublishCourse(ownerId, courseId).lifecycleStatus());

        BusinessException error = assertThrows(BusinessException.class, () -> service.unpublishCourse(ownerId, courseId));

        assertEquals(ErrorCode.INVALID_LIFECYCLE_TRANSITION, error.getCode());
    }

    @Test
    void archivesPublishedCourseAndRestoresItToDraft() {
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        assertEquals(CourseLifecycleStatus.ARCHIVED, service.archiveCourse(ownerId, courseId).lifecycleStatus());
        assertEquals(CourseLifecycleStatus.DRAFT, service.restoreCourse(ownerId, courseId).lifecycleStatus());
    }

    @Test
    void adminCanDisableAndEnableCourseButInstructorCannot() {
        UUID adminId = UUID.randomUUID();
        User admin = user(adminId, UserRole.ADMIN);
        when(users.findById(adminId)).thenReturn(Optional.of(admin));

        assertEquals(CourseLifecycleStatus.DISABLED, service.disableCourse(adminId, courseId).lifecycleStatus());
        assertEquals(CourseLifecycleStatus.DRAFT, service.enableCourse(adminId, courseId).lifecycleStatus());

        BusinessException error = assertThrows(BusinessException.class, () -> service.disableCourse(ownerId, courseId));
        assertEquals(ErrorCode.ADMIN_ROLE_REQUIRED, error.getCode());
    }

    @Test
    void addsActiveInstructorAndRejectsDuplicateOrInactiveAccounts() {
        UUID coInstructorId = UUID.randomUUID();
        User coInstructor = user(coInstructorId, UserRole.INSTRUCTOR);
        when(users.findById(coInstructorId)).thenReturn(Optional.of(coInstructor));
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        when(courseInstructors.existsById(new CourseInstructorId(courseId, coInstructorId))).thenReturn(false);
        when(courseInstructors.saveAndFlush(any(CourseInstructor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseDtos.InstructorResponse added = service.addCoInstructor(ownerId, courseId, new CourseDtos.AddInstructorRequest(coInstructorId));

        assertEquals(coInstructorId, added.instructorId());
        assertEquals(CourseInstructorRole.CO_INSTRUCTOR, added.courseRole());

        when(courseInstructors.existsById(new CourseInstructorId(courseId, coInstructorId))).thenReturn(true);
        BusinessException duplicate = assertThrows(BusinessException.class,
                () -> service.addCoInstructor(ownerId, courseId, new CourseDtos.AddInstructorRequest(coInstructorId)));
        assertEquals(ErrorCode.COURSE_INSTRUCTOR_ALREADY_ASSIGNED, duplicate.getCode());

        coInstructor.setAccountStatus(AccountStatus.DISABLED);
        BusinessException inactive = assertThrows(BusinessException.class,
                () -> service.addCoInstructor(ownerId, courseId, new CourseDtos.AddInstructorRequest(coInstructorId)));
        assertEquals(ErrorCode.INSTRUCTOR_ROLE_REQUIRED, inactive.getCode());
    }

    @Test
    void preventsOwnerRemovalAndRemovesCoInstructor() {
        BusinessException ownerError = assertThrows(BusinessException.class,
                () -> service.removeCoInstructor(ownerId, courseId, ownerId));
        assertEquals(ErrorCode.CANNOT_REMOVE_OWNER, ownerError.getCode());

        UUID coInstructorId = UUID.randomUUID();
        CourseInstructor link = new CourseInstructor();
        link.setCourseRole(CourseInstructorRole.CO_INSTRUCTOR);
        when(courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, coInstructorId)).thenReturn(Optional.of(link));

        CourseDtos.DeleteInstructorResponse result = service.removeCoInstructor(ownerId, courseId, coInstructorId);

        assertEquals(coInstructorId, result.deletedId());
        verify(courseInstructors).delete(link);
    }

    @Test
    void listsCourseInstructorsForCourseManager() {
        CourseInstructor link = new CourseInstructor();
        link.setInstructor(owner);
        link.setCourseRole(CourseInstructorRole.OWNER);
        when(courses.findById(courseId)).thenReturn(Optional.of(course));
        when(courseInstructors.findAllForCourse(courseId)).thenReturn(List.of(link));

        CourseDtos.InstructorList result = service.listCourseInstructors(ownerId, courseId);

        assertEquals(1, result.items().size());
        assertEquals(ownerId, result.items().getFirst().instructorId());
    }

    private User user(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }
}
