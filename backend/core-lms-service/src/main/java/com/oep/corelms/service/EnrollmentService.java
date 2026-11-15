package com.oep.corelms.service;

import com.oep.corelms.dto.student.MyEnrollmentResponse;
import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Students can only enroll with a valid invite code for the course; the course page itself is always browsable. */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseInviteCodeRepository inviteCodes;

    /** Maps to the response DTO here, still inside the transaction, so course/subject lazy-load safely. */
    @Transactional
    public MyEnrollmentResponse enroll(UUID courseId, UUID studentId, String inviteCode) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Course is not open for enrollment");
        }
        CourseInviteCode ic = inviteCode == null ? null : inviteCodes.findByCode(inviteCode).orElse(null);
        boolean valid = ic != null && ic.isActive()
                && ic.getCourse().getId().equals(courseId)
                && (ic.getExpiresAt() == null || ic.getExpiresAt().isAfter(Instant.now()));
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INVITE_CODE, "Invalid or expired invite code");
        }

        // (course_id, student_id) is unique: a previously WITHDRAWN/REMOVED row is reactivated in place
        // rather than inserted again; ACTIVE/COMPLETED is a real conflict.
        Optional<Enrollment> existing = enrollments.findByCourseIdAndStudentId(courseId, studentId);
        if (existing.isPresent()
                && (existing.get().getStatus() == EnrollmentStatus.ACTIVE || existing.get().getStatus() == EnrollmentStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED, "Already enrolled");
        }
        // NOTE: can race under concurrent enrolments; lock the course row when you harden this.
        if (course.getCapacity() != null
                && enrollments.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE) >= course.getCapacity()) {
            throw new BusinessException(ErrorCode.COURSE_FULL, "Course is full");
        }

        Enrollment e = existing.orElseGet(Enrollment::new);
        e.setCourse(course);
        e.setStudent(users.getReferenceById(studentId));
        e.setInviteCode(ic);
        e.setStatus(EnrollmentStatus.ACTIVE);
        e.setWithdrawnAt(null);
        e.setRemovedAt(null);
        return MyEnrollmentResponse.from(enrollments.save(e));
    }

    /** A student may withdraw from their own active enrollment at any time; re-entry needs a fresh invite code. */
    @Transactional
    public MyEnrollmentResponse withdraw(UUID enrollmentId, UUID studentId) {
        Enrollment e = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        if (!e.getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Not your enrollment");
        }
        if (e.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Can only withdraw from an active enrollment");
        }
        e.setStatus(EnrollmentStatus.WITHDRAWN);
        e.setWithdrawnAt(Instant.now());
        return MyEnrollmentResponse.from(enrollments.save(e));
    }
}
