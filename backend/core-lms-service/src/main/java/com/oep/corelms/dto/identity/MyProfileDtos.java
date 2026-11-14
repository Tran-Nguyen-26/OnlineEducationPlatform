package com.oep.corelms.dto.identity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MyProfileDtos {
    private MyProfileDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Response(UUID userId, String email, String role, String fullName, String phone,
                          String organization, String bio, LocalDate dateOfBirth,
                          String avatarUrl) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateRequest(@NotBlank @Size(max = 255) String fullName,
                                @Size(max = 50) String phone,
                                @Size(max = 255) String organization,
                                @Size(max = 4000) String bio,
                                @Past LocalDate dateOfBirth) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvatarUploadRequest(@NotBlank @Size(max = 255) String filename,
                                      @jakarta.validation.constraints.Positive long size) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvatarUploadTicket(String objectKey, String uploadUrl,
                                     Map<String, List<String>> headers) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AvatarConfirmRequest(@NotBlank @Size(max = 500) String objectKey) {}
}
