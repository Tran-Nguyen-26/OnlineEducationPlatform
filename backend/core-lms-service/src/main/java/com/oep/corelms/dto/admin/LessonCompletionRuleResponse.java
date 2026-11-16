package com.oep.corelms.dto.admin;

import java.math.BigDecimal;
import java.util.UUID;

/** configured=false means no row exists yet — the lesson can be marked COMPLETED freely. */
public record LessonCompletionRuleResponse(
        UUID lessonId,
        boolean configured,
        int minimumTimeSeconds,
        BigDecimal minVideoPercentage,
        boolean requireQuiz,
        boolean requireAssignment) {
}
