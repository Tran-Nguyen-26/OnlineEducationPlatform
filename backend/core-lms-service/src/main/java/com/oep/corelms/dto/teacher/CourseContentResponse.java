package com.oep.corelms.dto.teacher;

import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;

import java.util.List;
import java.util.UUID;

/** Read-only mirror of the admin content tree, for a teacher assigned to the course (no edit rights, no progress). */
public record CourseContentResponse(UUID courseId, String title, List<ContentModule> modules) {

    public record ContentModule(UUID id, String title, String description, int position, List<ContentLesson> lessons) {
    }

    public record ContentLesson(UUID id, String title, String description, int position, boolean required, List<ContentItem> contents) {
    }

    public record ContentItem(
            UUID id,
            ContentType contentType,
            String title,
            String textContent,
            String externalUrl,
            String fileName,
            UploadStatus uploadStatus) {
    }
}
