package com.oep.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StudentAssignmentResponse(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String title,
        String instructions,
        Instant deadline,
        int maxSubmissions,
        boolean allowLateSubmission,
        boolean allowText,
        boolean allowFiles,
        BigDecimal maxScore,
        int submissionCount,
        boolean canSubmit,
        boolean graded,
        BigDecimal score) {
}
