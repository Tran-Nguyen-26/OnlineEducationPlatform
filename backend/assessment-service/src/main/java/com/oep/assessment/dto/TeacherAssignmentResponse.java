package com.oep.assessment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TeacherAssignmentResponse(
        UUID id,
        String title,
        String instructions,
        Instant deadline,
        BigDecimal maxScore,
        int submissionCount) {
}
