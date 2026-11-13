package com.oep.corelms.service;

import java.time.Instant;

final class ExamWindow {
    static final long GRACE_SECONDS = 30;
    private ExamWindow() {}

    static boolean mayEnter(Instant start, Instant end, int lateMinutes, Instant now) {
        return !now.isBefore(start) && now.isBefore(end) && !now.isAfter(start.plusSeconds(lateMinutes * 60L));
    }

    static boolean mayEdit(Instant cutoff, Instant now) {
        return now.isBefore(cutoff);
    }

    static boolean shouldFinalize(Instant cutoff, Instant now) {
        return !now.isBefore(cutoff.plusSeconds(GRACE_SECONDS));
    }
}
