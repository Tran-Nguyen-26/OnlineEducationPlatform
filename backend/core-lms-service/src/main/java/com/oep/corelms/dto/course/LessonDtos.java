package com.oep.corelms.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LessonDtos {
    private LessonDtos() {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateRequest(@NotBlank @Size(max = 255) String title, String description,
                                Integer position, Boolean isRequired) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(@Size(min = 1, max = 255) String title, String description,
                                Boolean isRequired) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(UUID id, UUID moduleId, String title, String description,
                           int position, boolean isRequired, Instant createdAt, Instant updatedAt) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageResponse(List<Response> items, CourseDtos.PageInfo pagination) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReorderResponse(boolean success, List<UUID> orderedIds) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeleteResponse(boolean success, UUID deletedId) {}
}
