package com.oep.corelms.service;

import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {
    @Mock CourseRepository courses;
    @Mock EnrollmentRepository enrollments;
    @Mock UserRepository users;
    @Mock CourseInstructorRepository instructors;
    @InjectMocks EnrollmentService service;
    UUID cid = UUID.randomUUID(), sid = UUID.randomUUID(), eid = UUID.randomUUID(), owner = UUID.randomUUID();
    Course course;
    User student;
    Enrollment enrollment;

    @BeforeEach void setup() {
        student = user(sid, UserRole.STUDENT);
        course = new Course(); course.setId(cid); course.setCapacity(50);
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        enrollment = new Enrollment(); enrollment.setId(eid); enrollment.setCourse(course); enrollment.setStudent(student);
    }
    User user(UUID id, UserRole role) {
        User u = new User(); u.setId(id); u.setRole(role); return u;
    }
    void student() { when(users.findById(sid)).thenReturn(Optional.of(student)); }
    void enrolling() {
        student(); when(courses.findByIdForUpdate(cid)).thenReturn(Optional.of(course));
        when(enrollments.findByCourseIdAndStudentId(cid, sid)).thenReturn(Optional.empty());
    }
    void existing(EnrollmentStatus status) {
        student(); when(courses.findByIdForUpdate(cid)).thenReturn(Optional.of(course));
        enrollment.setStatus(status);
        when(enrollments.findByCourseIdAndStudentId(cid, sid)).thenReturn(Optional.of(enrollment));
    }
    void mutation() {
        when(enrollments.findCourseIdById(eid)).thenReturn(Optional.of(cid));
        when(courses.findByIdForUpdate(cid)).thenReturn(Optional.of(course));
        when(enrollments.findById(eid)).thenReturn(Optional.of(enrollment));
    }
    void save() { when(enrollments.save(any())).thenAnswer(i -> {
        Enrollment e = i.getArgument(0); if (e.getId() == null) e.setId(eid); return e;
    }); }
    void fails(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    @Test void enrollsPublishedPublicCourse() {
        enrolling(); save(); var result = service.enroll(cid, sid);
        assertThat(result.status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(result.studentId()).isEqualTo(sid);
        InOrder order = inOrder(courses, enrollments);
        order.verify(courses).findByIdForUpdate(cid);
        order.verify(enrollments).findByCourseIdAndStudentId(cid, sid);
        order.verify(enrollments).countByCourseIdAndStatusIn(cid, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED));
        order.verify(enrollments).save(any());
    }
    @Test void missingCourse404() { student(); fails(ErrorCode.NOT_FOUND, () -> service.enroll(cid, sid)); }
    @Test void draft409() { enrolling(); course.setLifecycleStatus(CourseLifecycleStatus.DRAFT); fails(ErrorCode.COURSE_NOT_OPEN, () -> service.enroll(cid, sid)); }
    @Test void archived409() { enrolling(); course.setLifecycleStatus(CourseLifecycleStatus.ARCHIVED); fails(ErrorCode.COURSE_NOT_OPEN, () -> service.enroll(cid, sid)); }
    @Test void disabledCourse409() { enrolling(); course.setLifecycleStatus(CourseLifecycleStatus.DISABLED); fails(ErrorCode.COURSE_NOT_OPEN, () -> service.enroll(cid, sid)); }
    @Test void private403() { enrolling(); course.setVisibility(CourseVisibility.PRIVATE); fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); }
    @Test void full409CountsCompletedToo() {
        enrolling(); when(enrollments.countByCourseIdAndStatusIn(cid, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED))).thenReturn(50L);
        fails(ErrorCode.COURSE_FULL, () -> service.enroll(cid, sid)); verify(enrollments, never()).save(any());
    }
    @Test void zeroCapacityRejects() { enrolling(); course.setCapacity(0); fails(ErrorCode.COURSE_FULL, () -> service.enroll(cid, sid)); }
    @Test void unlimitedSkipsCount() { enrolling(); course.setCapacity(null); save(); service.enroll(cid, sid); verify(enrollments, never()).countByCourseIdAndStatusIn(any(), any()); }
    @Test void activeRetryReturnsSameIdEvenWhenClosedAndFull() {
        existing(EnrollmentStatus.ACTIVE); course.setVisibility(CourseVisibility.PRIVATE); course.setLifecycleStatus(CourseLifecycleStatus.ARCHIVED); course.setCapacity(0);
        assertThat(service.enroll(cid, sid).id()).isEqualTo(eid);
        verify(enrollments, never()).save(any()); verify(enrollments, never()).countByCourseIdAndStatusIn(any(), any());
    }
    @Test void completedRetryReturnsSameRecord() { existing(EnrollmentStatus.COMPLETED); assertThat(service.enroll(cid, sid).status()).isEqualTo(EnrollmentStatus.COMPLETED); }
    @Test void removedCannotRejoin() { existing(EnrollmentStatus.REMOVED); fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); }
    @Test void withdrawnReusesIdAndOriginalDate() {
        existing(EnrollmentStatus.WITHDRAWN); save(); Instant original = enrollment.getEnrolledAt(); enrollment.setWithdrawnAt(Instant.now());
        var result = service.enroll(cid, sid); assertThat(result.id()).isEqualTo(eid);
        assertThat(result.enrolledAt()).isEqualTo(original); assertThat(result.withdrawnAt()).isNull();
        assertThat(result.status()).isEqualTo(EnrollmentStatus.ACTIVE);
    }
    @Test void withdrawnStillChecksCapacity() {
        existing(EnrollmentStatus.WITHDRAWN); course.setCapacity(0); fails(ErrorCode.COURSE_FULL, () -> service.enroll(cid, sid));
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
    }
    @Test void withdrawnStillChecksVisibility() { existing(EnrollmentStatus.WITHDRAWN); course.setVisibility(CourseVisibility.PRIVATE); fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); }
    @Test void disabledAccountForbidden() { student(); student.setAccountStatus(AccountStatus.DISABLED); fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); verifyNoInteractions(courses); }
    @Test void instructorCannotEnrollAsStudent() { student(); student.setRole(UserRole.INSTRUCTOR); fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); }
    @Test void nonexistentAccountForbidden() { fails(ErrorCode.FORBIDDEN, () -> service.enroll(cid, sid)); }
    @Test void ownActiveEnrollmentCanWithdraw() { student(); mutation(); assertThat(service.withdraw(eid, sid).status()).isEqualTo(EnrollmentStatus.WITHDRAWN); assertThat(enrollment.getWithdrawnAt()).isNotNull(); }
    @Test void cannotWithdrawSomeoneElse() { student(); mutation(); enrollment.setStudent(user(UUID.randomUUID(), UserRole.STUDENT)); fails(ErrorCode.FORBIDDEN, () -> service.withdraw(eid, sid)); }
    @Test void withdrawRetryPreservesTimestamp() { student(); mutation(); enrollment.setStatus(EnrollmentStatus.WITHDRAWN); Instant date = Instant.now(); enrollment.setWithdrawnAt(date); assertThat(service.withdraw(eid, sid).withdrawnAt()).isEqualTo(date); }
    @Test void completedCannotWithdraw() { student(); mutation(); enrollment.setStatus(EnrollmentStatus.COMPLETED); fails(ErrorCode.ENROLLMENT_STATE_CONFLICT, () -> service.withdraw(eid, sid)); }
    @Test void removedCannotWithdrawToBypassBan() { student(); mutation(); enrollment.setStatus(EnrollmentStatus.REMOVED); fails(ErrorCode.ENROLLMENT_STATE_CONFLICT, () -> service.withdraw(eid, sid)); }
    @Test void missingEnrollment404() { student(); fails(ErrorCode.NOT_FOUND, () -> service.withdraw(eid, sid)); }
    @Test void ownerCanRemove() {
        when(users.findById(owner)).thenReturn(Optional.of(user(owner, UserRole.INSTRUCTOR))); mutation();
        when(instructors.existsByCourseIdAndInstructorIdAndCourseRole(cid, owner, CourseInstructorRole.OWNER)).thenReturn(true);
        assertThat(service.remove(eid, owner).status()).isEqualTo(EnrollmentStatus.REMOVED); assertThat(enrollment.getRemovedAt()).isNotNull();
        Instant removed = enrollment.getRemovedAt(); assertThat(service.remove(eid, owner).removedAt()).isEqualTo(removed);
    }
    @Test void creatorWithoutOwnerMembershipCannotRemove() {
        User creator = user(owner, UserRole.INSTRUCTOR); course.setCreatedBy(creator);
        when(users.findById(owner)).thenReturn(Optional.of(creator)); mutation(); fails(ErrorCode.FORBIDDEN, () -> service.remove(eid, owner));
    }
    @Test void adminCannotRemove() { when(users.findById(owner)).thenReturn(Optional.of(user(owner, UserRole.ADMIN))); fails(ErrorCode.FORBIDDEN, () -> service.remove(eid, owner)); verifyNoInteractions(enrollments); }
    @Test void studentListsOnlyOwnEnrollments() { student(); when(enrollments.findByStudentId(eq(sid), any())).thenReturn(new PageImpl<>(List.of(enrollment))); assertThat(service.list(sid, null, 0, 20).getContent()).hasSize(1); verify(enrollments, never()).findByCourseId(any(), any()); }
    @Test void ownerListsCourse() {
        when(users.findById(owner)).thenReturn(Optional.of(user(owner, UserRole.INSTRUCTOR)));
        when(instructors.existsByCourseIdAndInstructorIdAndCourseRole(cid, owner, CourseInstructorRole.OWNER)).thenReturn(true);
        when(enrollments.findByCourseId(eq(cid), any())).thenReturn(Page.empty()); assertThat(service.list(owner, cid, 0, 20)).isEmpty();
    }
    @Test void coInstructorCannotListCourse() { when(users.findById(owner)).thenReturn(Optional.of(user(owner, UserRole.INSTRUCTOR))); fails(ErrorCode.FORBIDDEN, () -> service.list(owner, cid, 0, 20)); }
    @Test void studentCannotListCourseRoster() { student(); fails(ErrorCode.FORBIDDEN, () -> service.list(sid, cid, 0, 20)); }
    @Test void rejectsInvalidPagination() {
        fails(ErrorCode.VALIDATION_FAILED, () -> service.list(sid, null, -1, 20));
        fails(ErrorCode.VALIDATION_FAILED, () -> service.list(sid, null, 0, 101));
        fails(ErrorCode.VALIDATION_FAILED, () -> service.list(sid, null, 0, 0));
    }
}
