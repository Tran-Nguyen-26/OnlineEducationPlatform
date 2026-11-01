package com.oep.corelms.dto.course;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

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
                                @Min(0) Integer position,
                                @Min(1) Integer durationSeconds) {
        public CreateRequest(ContentType contentType, String title, String textContent, String externalUrl,
                             String fileName, String mimeType, Long fileSize, Integer position) {
            this(contentType, title, textContent, externalUrl, fileName, mimeType, fileSize, position, null);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(@Size(max = 255) String title,
                                String textContent,
                                @Size(max = 1000) String externalUrl) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(UUID id, UUID lessonId, ContentType contentType, String title,
                           String textContent, String externalUrl, String fileName, String mimeType,
                           Long fileSize, UploadStatus uploadStatus, int position, int versionNumber,
                           Instant createdAt, Instant updatedAt, Integer durationSeconds) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageResponse(List<Response> items, CourseDtos.PageInfo pagination) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeleteResponse(boolean success, UUID deletedId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderResponse(boolean success, List<UUID> orderedIds) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UploadUrlResponse(String uploadUrl, String method, Map<String, String> requiredHeaders,
                                    Instant expiresAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MultipartPartUrl(int partNumber, String uploadUrl) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MultipartUploadResponse(String uploadId, int partSizeBytes, List<MultipartPartUrl> parts,
                                          Instant expiresAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MultipartPart(int partNumber, @NotEmpty @Size(max = 512) String eTag) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CompleteMultipartRequest(@NotEmpty List<@Valid MultipartPart> parts) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AccessUrlResponse(String accessUrl, String method, Instant expiresAt, String fileName) {}
}
