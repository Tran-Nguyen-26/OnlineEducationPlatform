package com.oep.corelms.dto.admin;

import java.time.Instant;

public record ResetLinkResponse(String token, Instant expiresAt) {
}
