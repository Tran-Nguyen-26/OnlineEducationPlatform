package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.EnrollmentStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateEnrollmentStatusRequest(@NotNull EnrollmentStatus status) {
}
