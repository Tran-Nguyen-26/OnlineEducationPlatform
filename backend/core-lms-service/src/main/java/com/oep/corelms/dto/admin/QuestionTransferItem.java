package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** One question in the import/export format (JSON as-is; CSV is mapped to/from this shape). */
public record QuestionTransferItem(@NotNull QuestionType questionType, @NotBlank String questionText, String keywordAnswer,
                                   List<OptionInput> options) {
}
