package com.oep.corelms.dto.social;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {
    private SessionDtos() {}

    public record CreateRequest(@NotBlank String title, @NotNull Instant startsAt, Instant endsAt, String location, String note) {}

    /** counts only populated for staff; myStatus only for a student's own record (null = not yet marked). */
    public record SessionResponse(UUID id, String title, Instant startsAt, Instant endsAt, String location, String note,
                                  int present, int late, int excused, int absent, int marked, String myStatus) {}

    public record RosterRow(UUID enrollmentId, UUID studentId, String studentName, String studentEmail, String status, String note) {}

    public record MarkEntry(@NotNull UUID enrollmentId, @NotBlank String status, String note) {}

    public record MarkRequest(@Valid @NotNull List<MarkEntry> entries) {}

    /** attendanceRate = (PRESENT + LATE + EXCUSED) / marked sessions, percent; null when nothing marked yet. */
    public record SummaryRow(UUID enrollmentId, UUID studentId, String studentName, String studentEmail, int sessionsMarked,
                             int present, int late, int excused, int absent, Double attendanceRate) {}
}
