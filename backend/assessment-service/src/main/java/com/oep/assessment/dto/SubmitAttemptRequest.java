package com.oep.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SubmitAttemptRequest(@Valid List<AnswerInput> answers) {

    public record AnswerInput(@NotNull UUID questionId, List<UUID> selectedOptionIds, String textAnswer) {
    }
}
