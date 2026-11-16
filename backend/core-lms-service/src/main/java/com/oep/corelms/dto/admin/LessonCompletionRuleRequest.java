package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LessonCompletionRuleRequest(
        @Min(0) int minimumTimeSeconds,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minVideoPercentage,
        boolean requireQuiz,
        boolean requireAssignment) {
}
