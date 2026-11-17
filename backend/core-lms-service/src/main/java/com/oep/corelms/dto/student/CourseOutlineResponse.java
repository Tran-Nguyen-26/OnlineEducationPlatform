package com.oep.corelms.dto.student;

import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.LessonProgressStatus;
import com.oep.coremodel.enums.UploadStatus;

import java.util.List;
import java.util.UUID;

public record CourseOutlineResponse(UUID courseId, String title, String enrollmentStatus, boolean readOnly, String learningMode, List<OutlineModule> modules) {

    /** What the student must do before the lesson can be marked complete (null fields = not required). */
    public record CompletionRequirement(int minimumTimeSeconds, Integer minVideoPercentage, boolean requireQuiz, boolean requireAssignment) {
    }


    public record OutlineModule(UUID id, String title, String description, int position, List<OutlineLesson> lessons) {
    }

    public record OutlineLesson(
            UUID id,
            String title,
            String description,
            int position,
            boolean required,
            boolean locked,
            LessonProgressStatus progressStatus,
            int watchedPercent,
            int timeSpentSeconds,
            CompletionRequirement completionRequirement,
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
            UploadStatus uploadStatus,
            com.oep.coremodel.enums.TranscodeStatus transcodeStatus) {
    }
}
