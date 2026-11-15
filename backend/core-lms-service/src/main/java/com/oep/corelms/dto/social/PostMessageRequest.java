package com.oep.corelms.dto.social;

import jakarta.validation.constraints.NotBlank;

public record PostMessageRequest(@NotBlank String content) {
}
