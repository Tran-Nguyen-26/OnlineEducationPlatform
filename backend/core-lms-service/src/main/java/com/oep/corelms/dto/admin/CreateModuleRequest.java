package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateModuleRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        int position) {
}
