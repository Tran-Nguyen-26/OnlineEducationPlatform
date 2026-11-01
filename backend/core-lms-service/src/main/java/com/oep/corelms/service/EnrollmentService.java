package com.oep.corelms.service;

import com.oep.corelms.dto.enrollment.EnrollmentResponse;
import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
// READ_COMMITTED observes the preceding lock holder's committed enrollment.
// All operations use the primary, including authorization and list reads.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EnrollmentService {
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseInstructorRepository instructors;

    public EnrollmentResponse enroll(UUID courseId, UUID studentId) {
        User student = requireUser(studentId, UserRole.STUDENT);
        Course course = lockCourse(courseId);
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId).orElse(null);
        if (enrollment != null) {
            if (enrollment.getStatus() == EnrollmentStatus.REMOVED)
                throw error(ErrorCode.FORBIDDEN, "Removed students cannot enroll again");
            if (enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
                return EnrollmentResponse.from(enrollment);
        }
        if (course.getVisibility() != CourseVisibility.PUBLIC)
            throw error(ErrorCode.FORBIDDEN, "Direct enrollment requires a public course");
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED)
            throw error(ErrorCode.COURSE_NOT_OPEN, "Course is not published");
        if (course.getCapacity() != null && enrollments.countByCourseIdAndStatusIn(courseId,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)) >= course.getCapacity())
            throw error(ErrorCode.COURSE_FULL, "Course is full");
        if (enrollment == null) {
            enrollment = new Enrollment();
            enrollment.setCourse(course);
            enrollment.setStudent(student);
        }
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setWithdrawnAt(null);
        // Preserve original enrolledAt and all lesson progress on reactivation.
        return EnrollmentResponse.from(enrollments.save(enrollment));
    }

    public EnrollmentResponse withdraw(UUID id, UUID studentId) {
        requireUser(studentId, UserRole.STUDENT);
        Enrollment enrollment = lockedEnrollment(id);
        if (!enrollment.getStudent().getId().equals(studentId))
            throw error(ErrorCode.FORBIDDEN, "You can only withdraw your own enrollment");
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) return EnrollmentResponse.from(enrollment);
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE)
            throw error(ErrorCode.ENROLLMENT_STATE_CONFLICT, "Only active enrollments can be withdrawn");
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(Instant.now());
        return EnrollmentResponse.from(enrollment);
    }

    public EnrollmentResponse remove(UUID id, UUID instructorId) {
        requireUser(instructorId, UserRole.INSTRUCTOR);
        Enrollment enrollment = lockedEnrollment(id);
        requireOwner(enrollment.getCourse().getId(), instructorId);
        if (enrollment.getStatus() != EnrollmentStatus.REMOVED) {
            enrollment.setStatus(EnrollmentStatus.REMOVED);
            enrollment.setRemovedAt(Instant.now());
        }
        return EnrollmentResponse.from(enrollment);
    }

    public Page<EnrollmentResponse> list(UUID actorId, UUID courseId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw error(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size between 1 and 100");
        var pageable = PageRequest.of(page, size, Sort.by("enrolledAt").descending().and(Sort.by("id")));
        if (courseId == null) {
            requireUser(actorId, UserRole.STUDENT);
            return enrollments.findByStudentId(actorId, pageable).map(EnrollmentResponse::from);
        }
        requireUser(actorId, UserRole.INSTRUCTOR);
        requireOwner(courseId, actorId);
        return enrollments.findByCourseId(courseId, pageable).map(EnrollmentResponse::from);
    }

    private Enrollment lockedEnrollment(UUID id) {
        UUID courseId = enrollments.findCourseIdById(id)
                .orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Enrollment not found"));
        lockCourse(courseId);
        return enrollments.findById(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Enrollment not found"));
    }
    private Course lockCourse(UUID id) {
        return courses.findByIdForUpdate(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Course not found"));
    }
    private User requireUser(UUID id, UserRole role) {
        User user = users.findById(id).orElseThrow(() -> error(ErrorCode.FORBIDDEN, "Account unavailable"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE || user.getRole() != role)
            throw error(ErrorCode.FORBIDDEN, "Account is not permitted to perform this action");
        return user;
    }
    private void requireOwner(UUID courseId, UUID actorId) {
        if (!instructors.existsByCourseIdAndInstructorIdAndCourseRole(courseId, actorId, CourseInstructorRole.OWNER))
            throw error(ErrorCode.FORBIDDEN, "Only the course owner can manage enrollments");
    }
    private BusinessException error(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }
}