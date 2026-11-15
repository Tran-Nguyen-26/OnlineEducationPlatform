package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @NotNull Boolean active) {
}
