package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminEnrollmentResponse;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin-side view and lifecycle control over a course's roster (withdraw/remove/complete on a student's behalf). */
@Service
@RequiredArgsConstructor
public class AdminEnrollmentService {

    private final EnrollmentRepository enrollments;
    private final UserProfileRepository userProfiles;

    @Transactional(readOnly = true)
    public List<AdminEnrollmentResponse> list(UUID courseId) {
        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream().map(this::toResponse).toList();
    }

    /** Only ACTIVE can transition, and only to WITHDRAWN/REMOVED/COMPLETED — there is no path back to ACTIVE here. */
    @Transactional
    public AdminEnrollmentResponse updateStatus(UUID enrollmentId, EnrollmentStatus target) {
        Enrollment e = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        if (e.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Can only change status of an active enrollment");
        }
        if (target != EnrollmentStatus.WITHDRAWN && target != EnrollmentStatus.REMOVED && target != EnrollmentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Unsupported target status");
        }

        Instant now = Instant.now();
        e.setStatus(target);
        switch (target) {
            case WITHDRAWN -> e.setWithdrawnAt(now);
            case REMOVED -> e.setRemovedAt(now);
            case COMPLETED -> e.setCompletedAt(now);
            default -> throw new IllegalStateException("Unreachable");
        }
        return toResponse(enrollments.save(e));
    }

    private AdminEnrollmentResponse toResponse(Enrollment e) {
        User student = e.getStudent();
        String fullName = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(null);
        return new AdminEnrollmentResponse(e.getId(), student.getId(), student.getEmail(), fullName, e.getStatus(),
                e.getEnrolledAt(), e.getCompletedAt(), e.getWithdrawnAt(), e.getRemovedAt());
    }
}
