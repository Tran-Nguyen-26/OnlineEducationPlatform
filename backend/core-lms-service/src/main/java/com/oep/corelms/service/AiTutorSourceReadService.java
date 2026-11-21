package com.oep.corelms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Authoritative, primary-routed M2 read for a versioned M7 index job. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorSourceReadService {
    public record Source(
            @JsonProperty("content_id") UUID contentId,
            @JsonProperty("course_id") UUID courseId,
            @JsonProperty("lesson_id") UUID lessonId,
            @JsonProperty("version_number") int versionNumber,
            @JsonProperty("content_type") ContentType contentType,
            @JsonProperty("source_title") String sourceTitle,
            @JsonProperty("text_content") String textContent,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("mime_type") String mimeType,
            @JsonProperty("file_size") Long fileSize,
            @JsonProperty("download_url") String downloadUrl) {}

    private final LessonContentRepository contents;
    private final StorageService storage;

    @Transactional
    public Source read(UUID contentId, UUID courseId, UUID lessonId, int versionNumber) {
        LessonContent content = contents.findById(contentId)
                .orElseThrow(AiTutorSourceReadService::notFound);
        if (versionNumber < 1 || content.getVersionNumber() != versionNumber
                || content.getUploadStatus() != UploadStatus.AVAILABLE
                || content.getLesson() == null
                || !lessonId.equals(content.getLesson().getId())
                || content.getLesson().getModule() == null
                || content.getLesson().getModule().getCourse() == null
                || !courseId.equals(content.getLesson().getModule().getCourse().getId())
                || content.getLesson().getModule().getCourse().getLifecycleStatus()
                    != CourseLifecycleStatus.PUBLISHED
                || !AiTutorIndexOutbox.eligibleType(content)) {
            throw notFound();
        }
        String title = content.getTitle();
        if (title == null || title.isBlank()) title = content.getFileName();
        if (title == null || title.isBlank()) title = "Lesson content";
        if (content.getContentType() == ContentType.TEXT) {
            if (content.getTextContent() == null || content.getTextContent().isBlank()) throw notFound();
            return new Source(contentId, courseId, lessonId, versionNumber,
                    content.getContentType(), title, content.getTextContent(),
                    null, null, null, null);
        }
        if (content.getObjectKey() == null || content.getObjectKey().isBlank()
                || content.getFileSize() == null || content.getFileSize() <= 0) throw notFound();
        long maximumBytes = AiTutorLearningFilePolicy.maximumBytes(
                content.getContentType(), content.getFileName(), content.getMimeType());
        if (maximumBytes <= 0 || content.getFileSize() > maximumBytes) throw notFound();
        String downloadUrl;
        try {
            downloadUrl = storage.presignView(content.getObjectKey());
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI source storage is unavailable");
        }
        return new Source(contentId, courseId, lessonId, versionNumber,
                content.getContentType(), title, null,
                content.getFileName(), content.getMimeType(), content.getFileSize(),
                downloadUrl);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "AI source not found");
    }
}
