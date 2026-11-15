package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignInstructorRequest(@NotNull UUID instructorId) {
}
