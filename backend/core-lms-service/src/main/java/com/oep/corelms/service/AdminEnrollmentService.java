package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminEnrollmentResponse;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.course.Course;
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
    private final TuitionPaymentService tuitionPaymentService;
    private final CertificateService certificateService;
    private final WaitlistService waitlistService;

    @Transactional(readOnly = true)
    public List<AdminEnrollmentResponse> list(UUID courseId) {
        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream().map(this::toResponse).toList();
    }

    /** UTF-8 BOM up front so Excel renders Vietnamese diacritics correctly when the file is double-clicked. */
    @Transactional(readOnly = true)
    public String exportCsv(UUID courseId) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Email,Ho ten,Trang thai,Ngay ghi danh,Hoc phi,Da dong,Trang thai thanh toan\n");
        for (AdminEnrollmentResponse row : list(courseId)) {
            csv.append(csvField(row.studentEmail())).append(',')
                    .append(csvField(row.studentFullName())).append(',')
                    .append(csvField(row.status().name())).append(',')
                    .append(csvField(row.enrolledAt().toString())).append(',')
                    .append(csvField(row.feeAmount() == null ? "" : row.feeAmount().toPlainString())).append(',')
                    .append(csvField(row.totalPaid() == null ? "" : row.totalPaid().toPlainString())).append(',')
                    .append(csvField(row.paymentStatus().name())).append('\n');
        }
        return csv.toString();
    }

    private String csvField(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * From ACTIVE: WITHDRAWN/REMOVED/COMPLETED (no path back to ACTIVE here).
     * From WAITLISTED: REMOVED (cancel the waitlist spot) or ACTIVE (manual promote — bypasses the capacity
     * check deliberately, since it's an explicit Admin override; see WaitlistService for the automatic path).
     */
    @Transactional
    public AdminEnrollmentResponse updateStatus(UUID enrollmentId, EnrollmentStatus target) {
        Enrollment e = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        EnrollmentStatus from = e.getStatus();
        boolean validTransition = (from == EnrollmentStatus.ACTIVE
                && (target == EnrollmentStatus.WITHDRAWN || target == EnrollmentStatus.REMOVED || target == EnrollmentStatus.COMPLETED))
                || (from == EnrollmentStatus.WAITLISTED
                && (target == EnrollmentStatus.REMOVED || target == EnrollmentStatus.ACTIVE));
        if (!validTransition) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Unsupported status transition");
        }

        Instant now = Instant.now();
        e.setStatus(target);
        switch (target) {
            case WITHDRAWN -> e.setWithdrawnAt(now);
            case REMOVED -> e.setRemovedAt(now);
            case COMPLETED -> e.setCompletedAt(now);
            case ACTIVE -> { /* manual promotion from WAITLISTED, no extra timestamp */ }
            default -> throw new IllegalStateException("Unreachable");
        }
        Enrollment saved = enrollments.save(e);
        if (target == EnrollmentStatus.COMPLETED) {
            certificateService.issueIfAbsent(saved);
        }
        if (from == EnrollmentStatus.ACTIVE && target != EnrollmentStatus.ACTIVE) {
            waitlistService.promoteNext(saved.getCourse().getId());
        }
        return toResponse(saved);
    }

    private AdminEnrollmentResponse toResponse(Enrollment e) {
        User student = e.getStudent();
        String fullName = userProfiles.findById(student.getId()).map(UserProfile::getFullName).orElse(null);
        Course course = e.getCourse();
        java.math.BigDecimal totalPaid = tuitionPaymentService.totalPaid(e.getId());
        return new AdminEnrollmentResponse(e.getId(), student.getId(), student.getEmail(), fullName, e.getStatus(),
                e.getEnrolledAt(), e.getCompletedAt(), e.getWithdrawnAt(), e.getRemovedAt(),
                course.getFeeAmount(), totalPaid, tuitionPaymentService.status(course.getFeeAmount(), totalPaid));
    }
}
