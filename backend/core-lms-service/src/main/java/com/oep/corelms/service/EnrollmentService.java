package com.oep.corelms.service;

import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Example of using core-model: capacity check + private course invite code. */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseInviteCodeRepository inviteCodes;

    @Transactional
    public Enrollment enroll(UUID courseId, UUID studentId, String inviteCode) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Course is not open for enrollment");
        }
        if (course.getVisibility() == CourseVisibility.PRIVATE) {
            CourseInviteCode ic = inviteCode == null ? null : inviteCodes.findByCode(inviteCode).orElse(null);
            boolean valid = ic != null && ic.isActive()
                    && ic.getCourse().getId().equals(courseId)
                    && (ic.getExpiresAt() == null || ic.getExpiresAt().isAfter(Instant.now()));
            if (!valid) {
                throw new BusinessException(ErrorCode.INVALID_INVITE_CODE, "Invalid or expired invite code");
            }
        }
        // (course_id, student_id) is unique: a WITHDRAWN/REMOVED row would need reactivation logic later
        if (enrollments.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED, "Already enrolled");
        }
        // NOTE: can race under concurrent enrolments; lock the course row when you harden this.
        if (course.getCapacity() != null
                && enrollments.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE) >= course.getCapacity()) {
            throw new BusinessException(ErrorCode.COURSE_FULL, "Course is full");
        }

        Enrollment e = new Enrollment();
        e.setCourse(course);
        e.setStudent(users.getReferenceById(studentId));
        return enrollments.save(e);
    }
}
