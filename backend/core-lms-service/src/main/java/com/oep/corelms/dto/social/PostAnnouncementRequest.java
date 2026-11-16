package com.oep.corelms.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostAnnouncementRequest(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 5000) String content) {
}
