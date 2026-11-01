package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "Trạng thái tài khoản không được để trống")
        AccountStatus status,
        String reason
) {}
