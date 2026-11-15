package com.oep.assessment.dto;

import com.oep.coremodel.enums.AssignmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MySubmissionResponse(
        UUID id,
        String textContent,
        int submissionCount,
        AssignmentStatus status,
        Instant submittedAt,
        boolean late,
        List<SubmissionFileResponse> files,
        BigDecimal score,
        String feedback) {
}
