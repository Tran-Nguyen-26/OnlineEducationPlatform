package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.ContentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLessonContentRequest(
        @NotNull ContentType contentType,
        @Size(max = 255) String title,
        int position,
        String textContent,
        String externalUrl) {
}
