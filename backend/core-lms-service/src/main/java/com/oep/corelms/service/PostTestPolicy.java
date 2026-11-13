package com.oep.corelms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

final class PostTestPolicy {
    static final Duration COOLDOWN = Duration.ofHours(24);

    enum State { AVAILABLE, REMEDIATION_REQUIRED, REMEDIATION_AVAILABLE, EXHAUSTED }

    private PostTestPolicy() {}

    static State evaluate(int regularAttempts, int limit, boolean everPassed, boolean usedRemediation,
                          Instant lastSubmission, Instant now, boolean videosComplete) {
        if (usedRemediation) return State.EXHAUSTED;
        if (regularAttempts < limit) return State.AVAILABLE;
        if (everPassed || lastSubmission == null) return State.EXHAUSTED;
        return !now.isBefore(lastSubmission.plus(COOLDOWN)) && videosComplete
                ? State.REMEDIATION_AVAILABLE : State.REMEDIATION_REQUIRED;
    }

    static BigDecimal aggregate(List<BigDecimal> newestFirst, String policy) {
        if (newestFirst.isEmpty()) return null;
        return switch (policy) {
            case "LATEST" -> newestFirst.getFirst();
            case "AVERAGE" -> newestFirst.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(newestFirst.size()), 2, RoundingMode.HALF_UP);
            default -> newestFirst.stream().max(BigDecimal::compareTo).orElseThrow();
        };
    }
}
