package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.QuestionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record AddQuestionRequest(
        @NotNull QuestionType questionType,
        @NotBlank String questionText,
        String keywordAnswer,
        List<OptionInput> options,
        int position,
        @Positive BigDecimal points) {
}
