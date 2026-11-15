package com.oep.assessment.dto;

import com.oep.coremodel.enums.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Options never carry which one is correct — that would let the student read the answer out of the network tab. */
public record StartAttemptResponse(
        UUID attemptId,
        int attemptNumber,
        Instant startedAt,
        Integer timeLimitSeconds,
        List<QuestionForAttempt> questions) {

    public record QuestionForAttempt(
            UUID questionId,
            QuestionType questionType,
            String questionText,
            int position,
            BigDecimal points,
            List<OptionForAttempt> options) {
    }

    public record OptionForAttempt(UUID id, String optionText, int position) {
    }
}
