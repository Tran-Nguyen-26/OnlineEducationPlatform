package com.oep.corelms.dto.social;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record PostMessageRequest(@NotBlank String content, UUID lessonId) {
}
