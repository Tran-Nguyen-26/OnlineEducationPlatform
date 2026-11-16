package com.oep.assessment.dto;

import java.time.Instant;
import java.util.UUID;

public record PendingGradingResponse(
        UUID submissionId,
        UUID assignmentId,
        String assignmentTitle,
        UUID courseId,
        String courseTitle,
        String studentName,
        String studentEmail,
        String status,
        boolean late,
        Instant submittedAt) {
}
