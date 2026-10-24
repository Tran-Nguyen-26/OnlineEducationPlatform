package com.oep.corelms.dto.course;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LessonContentDtos {
    private LessonContentDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateRequest(@NotNull ContentType contentType,
                                @Size(max = 255) String title,
                                String textContent,
                                @Size(max = 1000) String externalUrl,
                                @Size(max = 255) String fileName,
                                @Size(max = 100) String mimeType,
                                @Min(1) Long fileSize,
                                @Min(0) Integer position) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(@Size(max = 255) String title,
                                String textContent,
                                @Size(max = 1000) String externalUrl) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(UUID id, UUID lessonId, ContentType contentType, String title,
                           String textContent, String externalUrl, String fileName, String mimeType,
                           Long fileSize, UploadStatus uploadStatus, int position, int versionNumber,
                           Instant createdAt, Instant updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageResponse(List<Response> items, CourseDtos.PageInfo pagination) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeleteResponse(boolean success, UUID deletedId) {}
}
