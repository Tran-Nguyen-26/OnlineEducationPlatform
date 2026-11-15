package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull AccountStatus status) {
}
