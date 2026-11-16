package com.oep.coremodel.event;

import java.util.Map;

/** Groups notification event types into user-switchable categories; unlisted types (system, certificate) are always delivered. */
public final class NotificationCategory {

    private NotificationCategory() {}

    public static final String COURSE_UPDATES = "COURSE_UPDATES";
    public static final String GRADING = "GRADING";
    public static final String REMINDERS = "REMINDERS";
    public static final String MESSAGES = "MESSAGES";

    public static final java.util.List<String> ALL = java.util.List.of(COURSE_UPDATES, GRADING, REMINDERS, MESSAGES);

    private static final Map<String, String> BY_EVENT = Map.ofEntries(
            Map.entry("COURSE_ANNOUNCEMENT", COURSE_UPDATES),
            Map.entry("SESSION_SCHEDULED", COURSE_UPDATES),
            Map.entry("WAITLIST_PROMOTED", COURSE_UPDATES),
            Map.entry("ASSIGNMENT_GRADED", GRADING),
            Map.entry("QUIZ_GRADED", GRADING),
            Map.entry("QUIZ_SCORE_ADJUSTED", GRADING),
            Map.entry("APPEAL_RESOLVED", GRADING),
            Map.entry("APPEAL_CREATED", GRADING),
            Map.entry("ASSIGNMENT_SUBMITTED", GRADING),
            Map.entry("QUIZ_DEADLINE_SOON", REMINDERS),
            Map.entry("ASSIGNMENT_DEADLINE_SOON", REMINDERS),
            Map.entry("INVITE_CODE_EXPIRING", REMINDERS),
            Map.entry("COURSE_MESSAGE", MESSAGES));

    /** null = mandatory notification that cannot be switched off. */
    public static String of(String eventType) {
        return BY_EVENT.get(eventType);
    }
}
