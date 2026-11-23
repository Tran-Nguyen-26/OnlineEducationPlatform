package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentRequest(
        @NotBlank @Size(max = 255) String title,
        String instructions,
        UUID lessonId,
        Instant deadline,
        @Positive Integer maxSubmissions,
        Boolean allowLateSubmission,
        Boolean allowText,
        Boolean allowFiles,
        @Positive BigDecimal maxScore,
        Boolean blindGrading) {
}
