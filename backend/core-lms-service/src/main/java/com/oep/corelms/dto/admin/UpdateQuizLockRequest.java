package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotNull;

public record UpdateQuizLockRequest(@NotNull Boolean locked) {
}
