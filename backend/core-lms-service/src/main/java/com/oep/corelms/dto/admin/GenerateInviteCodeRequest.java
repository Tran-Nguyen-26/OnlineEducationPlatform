package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** maxUses null = unlimited (class code); 1 = single-use activation code (BRD). */
public record GenerateInviteCodeRequest(Instant expiresAt, @Positive Integer maxUses) {
}
