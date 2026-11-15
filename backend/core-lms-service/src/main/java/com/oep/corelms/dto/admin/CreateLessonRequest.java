package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        int position,
        boolean required) {
}
