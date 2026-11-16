package com.oep.corelms.dto.admin;

import com.oep.corelms.dto.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {}

    /** expected = feeAmount × (ACTIVE + COMPLETED enrollments); outstanding = expected − collected (never negative). */
    public record CourseRow(UUID courseId, String courseTitle, BigDecimal feeAmount, long paidEnrollments, long enrollments,
                            BigDecimal expected, BigDecimal collected, BigDecimal outstanding) {}

    public record OutstandingRow(UUID enrollmentId, UUID studentId, String studentName, String studentEmail, UUID courseId,
                                 String courseTitle, String enrollmentStatus, BigDecimal feeAmount, BigDecimal paid,
                                 BigDecimal due, PaymentStatus paymentStatus) {}

    public record PaymentRow(UUID paymentId, UUID enrollmentId, String studentName, String studentEmail, String courseTitle,
                             BigDecimal amount, String note, Instant paidAt, String recordedByEmail, String type, String receiptNo) {}

    public record Summary(BigDecimal expected, BigDecimal collected, BigDecimal outstanding) {}
}
