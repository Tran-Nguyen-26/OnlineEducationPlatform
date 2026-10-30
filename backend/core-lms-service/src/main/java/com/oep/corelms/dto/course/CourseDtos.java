package com.oep.corelms.dto.course;

import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.CourseInstructorRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.Instant;
import java.util.UUID;

public final class CourseDtos {
    private CourseDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateCourseRequest(@NotBlank @Size(max = 255) String title,
                                     String description,
                                     CourseVisibility visibility,
                                     @Min(1) Integer capacity,
                                     LearningMode learningMode,
                                     Instant startAt,
                                     Instant endAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateCourseRequest(@Size(min = 1, max = 255) String title,
                                     String description,
                                     CourseVisibility visibility,
                                     @Min(1) Integer capacity,
                                     LearningMode learningMode,
                                     Instant startAt,
                                     Instant endAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CourseResponse(UUID id, String title, String description,
                                 CourseVisibility visibility, CourseLifecycleStatus lifecycleStatus,
                                 Integer capacity, LearningMode learningMode,
                                 Instant startAt, Instant endAt, UUID createdBy,
                                 Instant createdAt, Instant updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PageInfo(int page, int pageSize, long total, boolean hasNext) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CoursePage(java.util.List<CourseResponse> items, PageInfo pagination) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstructorResponse(UUID instructorId, CourseInstructorRole courseRole, Instant addedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstructorList(java.util.List<InstructorResponse> items) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AddInstructorRequest(@NotNull UUID instructorId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeleteInstructorResponse(boolean success, UUID deletedId) {}
}
