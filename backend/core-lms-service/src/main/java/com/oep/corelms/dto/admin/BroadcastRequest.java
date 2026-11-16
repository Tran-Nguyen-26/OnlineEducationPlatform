package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BroadcastRequest(
        @NotNull Audience audience,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 2000) String message) {

    public enum Audience { ALL, STUDENTS, INSTRUCTORS }
}
