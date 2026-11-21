package com.oep.coremodel.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** M2-owned, versioned source identity; M7 fetches metadata and bytes separately. */
public record AiIndexRequestedEvent(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("content_id") UUID contentId,
        @JsonProperty("course_id") UUID courseId,
        @JsonProperty("lesson_id") UUID lessonId,
        @JsonProperty("version_number") int versionNumber,
        @JsonProperty("action") String action) {
}
