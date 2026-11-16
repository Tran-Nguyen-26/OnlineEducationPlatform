package com.oep.corelms.dto.student;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.coremodel.enums.EnrollmentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record MyProgressResponse(
        UUID enrollmentId,
        UUID courseId,
        String courseTitle,
        String subjectName,
        EnrollmentStatus status,
        int totalLessons,
        int completedLessons,
        double percent,
        BigDecimal feeAmount,
        BigDecimal totalPaid,
        PaymentStatus paymentStatus) {
}
