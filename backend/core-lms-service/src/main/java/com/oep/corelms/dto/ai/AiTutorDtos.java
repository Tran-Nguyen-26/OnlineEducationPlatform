package com.oep.corelms.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public final class AiTutorDtos {
    private AiTutorDtos() {}

    public record AskRequest(
            UUID conversationId,
            @NotNull UUID courseId,
            @NotNull UUID lessonId,
            @Min(0) @Max(86_400) int timestampSeconds,
            @NotNull @Size(min = 1, max = 500) String question,
            @Pattern(regexp = "(?i)vi(?:-VN)?|en(?:-US)?") String locale
    ) {}

    public record Citation(UUID chunkId, UUID contentId, UUID videoId, String title, String excerpt, int timestampSeconds) {}

    public record AskResponse(UUID conversationId, String answer, List<Citation> citations,
                              boolean retrievalOnly, boolean refused, int remainingDailyQuestions) {}
}
