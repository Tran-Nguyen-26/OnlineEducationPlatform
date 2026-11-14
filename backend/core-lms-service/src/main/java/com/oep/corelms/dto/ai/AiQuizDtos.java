package com.oep.corelms.dto.ai;

import com.oep.coremodel.entity.course.LearningOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class AiQuizDtos {
    private AiQuizDtos() {}

    public record GenerateRequest(@NotNull UUID courseId, @NotNull UUID lessonId,
                                  @NotNull UUID learningOutcomeId, UUID questionBankId,
                                  @NotNull LearningOutcome.Bloom bloomLevel,
                                  @Min(1) @Max(10) int count) {}

    public record EditOption(@NotBlank @Size(max = 2000) String text, boolean correct,
                             @Size(max = 2000) String explanation) {}

    public record ReviewRequest(@NotBlank @Size(max = 5000) String question,
                                @NotNull @Size(min = 4, max = 4) List<@Valid EditOption> options) {}

    public record DraftOption(UUID id, String text, boolean correct, String explanation) {}
    public record DraftQuestion(UUID id, UUID courseId, UUID lessonId, UUID learningOutcomeId,
                                String learningOutcomeCode, String bloomLevel, String question,
                                List<DraftOption> options) {}
    public record GeneratedBatch(UUID questionBankId, int createdCount, List<DraftQuestion> drafts) {}
}
