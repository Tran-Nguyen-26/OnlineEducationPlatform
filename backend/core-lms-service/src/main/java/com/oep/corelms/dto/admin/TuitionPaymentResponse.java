package com.oep.corelms.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TuitionPaymentResponse(
        UUID id,
        BigDecimal amount,
        String note,
        Instant paidAt,
        String recordedByEmail) {
}
