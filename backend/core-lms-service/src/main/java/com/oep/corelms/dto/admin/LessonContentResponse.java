package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;

import java.util.UUID;

public record LessonContentResponse(
        UUID id,
        UUID lessonId,
        ContentType contentType,
        String title,
        String textContent,
        String externalUrl,
        String fileName,
        String mimeType,
        Long fileSize,
        Integer durationSeconds,
        UploadStatus uploadStatus,
        int position,
        com.oep.coremodel.enums.TranscodeStatus transcodeStatus) {

    public static LessonContentResponse from(LessonContent content) {
        return new LessonContentResponse(
                content.getId(),
                content.getLesson().getId(),
                content.getContentType(),
                content.getTitle(),
                content.getTextContent(),
                content.getExternalUrl(),
                content.getFileName(),
                content.getMimeType(),
                content.getFileSize(),
                content.getDurationSeconds(),
                content.getUploadStatus(),
                content.getPosition(),
                content.getTranscodeStatus());
    }
}
