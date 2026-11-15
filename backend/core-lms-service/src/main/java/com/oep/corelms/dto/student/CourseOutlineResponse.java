package com.oep.corelms.dto.student;

import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.enums.UploadStatus;

import java.util.List;
import java.util.UUID;

public record CourseOutlineResponse(UUID courseId, String title, List<OutlineModule> modules) {

    public record OutlineModule(UUID id, String title, String description, int position, List<OutlineLesson> lessons) {
    }

    public record OutlineLesson(
            UUID id,
            String title,
            String description,
            int position,
            boolean required,
            LessonProgressStatus progressStatus,
            List<OutlineContent> contents) {
    }

    public record OutlineContent(
            UUID id,
            ContentType contentType,
            String title,
            String textContent,
            String externalUrl,
            String fileName,
            String mimeType,
            Integer durationSeconds,
            UploadStatus uploadStatus) {
    }
}
