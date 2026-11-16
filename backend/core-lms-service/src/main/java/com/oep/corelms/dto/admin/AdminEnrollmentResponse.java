package com.oep.corelms.dto.admin;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.coremodel.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminEnrollmentResponse(
        UUID enrollmentId,
        UUID studentId,
        String studentEmail,
        String studentFullName,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt,
        Instant withdrawnAt,
        Instant removedAt,
        BigDecimal feeAmount,
        BigDecimal totalPaid,
        PaymentStatus paymentStatus) {
}
