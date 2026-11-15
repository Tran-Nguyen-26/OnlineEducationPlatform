package com.oep.corelms.dto.admin;

import java.time.Instant;

public record GenerateInviteCodeRequest(Instant expiresAt) {
}
