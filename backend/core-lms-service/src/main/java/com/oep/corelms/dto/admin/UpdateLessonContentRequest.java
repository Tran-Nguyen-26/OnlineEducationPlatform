package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.Size;

public record UpdateLessonContentRequest(
        @Size(max = 255) String title,
        int position,
        String textContent,
        String externalUrl) {
}
