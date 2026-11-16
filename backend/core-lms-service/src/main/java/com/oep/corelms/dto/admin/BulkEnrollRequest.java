package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkEnrollRequest(@NotNull UUID inviteCodeId, @NotEmpty List<String> emails) {
}
