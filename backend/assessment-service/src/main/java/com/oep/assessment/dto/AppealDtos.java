package com.oep.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AppealDtos {
    private AppealDtos() {}

    public record CreateRequest(@NotBlank String targetType, @NotNull UUID targetId, @NotBlank String reason) {}

    /** newScore is optional when accepting: if present the grade/score is changed immediately. */
    public record ResolveRequest(boolean accepted, String response, BigDecimal newScore) {}

    public record AppealResponse(UUID id, String targetType, UUID targetId, String targetTitle, UUID courseId, String courseTitle,
                                 String studentName, String studentEmail, String reason, String status, String response,
                                 Instant createdAt, Instant resolvedAt) {}
}
