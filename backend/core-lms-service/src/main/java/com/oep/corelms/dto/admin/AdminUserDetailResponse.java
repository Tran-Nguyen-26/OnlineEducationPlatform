package com.oep.corelms.dto.admin;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID id,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        Instant createdAt,
        String fullName,
        String phone,
        LocalDate dateOfBirth,
        String organization,
        String bio,
        List<EnrollmentSummary> enrollments,
        List<AssignedCourse> assignedCourses) {

    public record EnrollmentSummary(UUID enrollmentId, UUID courseId, String courseTitle, String status, Instant enrolledAt,
                                    BigDecimal feeAmount, BigDecimal totalPaid, PaymentStatus paymentStatus) {}

    public record AssignedCourse(UUID courseId, String courseTitle, String lifecycleStatus) {}
}
