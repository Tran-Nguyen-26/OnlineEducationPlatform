package com.oep.corelms.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollRequest(@NotNull UUID courseId, @NotBlank String inviteCode) {
}
