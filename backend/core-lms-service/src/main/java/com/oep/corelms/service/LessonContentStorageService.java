package com.oep.corelms.service;

import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.corelms.storage.StorageProperties;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonContentStorageService {
    private final LessonContentRepository contents;
    private final CourseManagementService courses;
    private final ObjectStorageService storage;
    private final StorageProperties properties;

    @Transactional
    public LessonContentDtos.UploadUrlResponse issueUploadUrl(UUID actorId, UUID contentId) {
        LessonContent content = requireContentForUpdate(contentId);
        UUID courseId = courseId(content);
        courses.assertCourseManagerAccess(courseId, actorId);
        requireFileContent(content);
        if (content.getUploadStatus() != UploadStatus.PENDING && content.getUploadStatus() != UploadStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Upload URL is available only for PENDING or FAILED content");
        }
        if (content.getFileSize() == null || content.getFileSize() < 1
                || content.getFileSize() > FileUploadPolicy.MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "File size must be between 1 byte and 100 MiB");
        }

        String objectKey = content.getObjectKey();
        if (objectKey == null || content.getUploadStatus() == UploadStatus.FAILED) {
            objectKey = "content/" + UUID.randomUUID();
            content.setObjectKey(objectKey);
        }
        content.setUploadStatus(UploadStatus.PENDING);
        contents.saveAndFlush(content);

        Duration ttl = Duration.ofSeconds(properties.getUploadUrlTtlSeconds());
        String url = storage.presignPut(objectKey, content.getMimeType(), ttl);
        return new LessonContentDtos.UploadUrlResponse(url, "PUT",
                Map.of("Content-Type", content.getMimeType()), Instant.now().plus(ttl));
    }

    @Transactional
    public LessonContentDtos.Response completeUpload(UUID actorId, UUID contentId) {
        LessonContent content = requireContentForUpdate(contentId);
        UUID courseId = courseId(content);
        courses.assertCourseManagerAccess(courseId, actorId);
        requireFileContent(content);
        if (content.getUploadStatus() == UploadStatus.AVAILABLE) return response(content);
        if (content.getUploadStatus() != UploadStatus.PENDING || content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Content has no pending upload to complete");
        }

        ObjectStorageService.ObjectMetadata metadata = storage.stat(content.getObjectKey());
        if (metadata.size() > FileUploadPolicy.MAX_FILE_SIZE_BYTES) {
            storage.delete(content.getObjectKey());
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "Uploaded file exceeds the 100 MiB limit");
        }
        if (content.getFileSize() == null || metadata.size() != content.getFileSize()
                || metadata.contentType() == null || !metadata.contentType().equalsIgnoreCase(content.getMimeType())) {
            storage.delete(content.getObjectKey());
            throw new BusinessException(ErrorCode.UPLOAD_METADATA_MISMATCH,
                    "Uploaded object size or Content-Type does not match the declared file metadata");
        }

        content.setUploadStatus(UploadStatus.UPLOADED);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        return response(contents.saveAndFlush(content));
    }

    @Transactional
    public LessonContentDtos.AccessUrlResponse getAccessUrl(UUID actorId, UUID contentId) {
        LessonContent content = contents.findById(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONTENT, "Content not found"));
        UUID courseId = courseId(content);
        courses.assertCourseReadAccess(courseId, actorId);
        requireFileContent(content);
        if (content.getUploadStatus() != UploadStatus.AVAILABLE || content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Content is not available for download");
        }

        Duration ttl = Duration.ofSeconds(properties.getAccessUrlTtlSeconds());
        String url = storage.presignGet(content.getObjectKey(), ttl);
        return new LessonContentDtos.AccessUrlResponse(url, "GET", Instant.now().plus(ttl), content.getFileName());
    }

    private LessonContent requireContentForUpdate(UUID contentId) {
        return contents.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONTENT, "Content not found"));
    }

    private UUID courseId(LessonContent content) {
        return content.getLesson().getModule().getCourse().getId();
    }

    private void requireFileContent(LessonContent content) {
        if (!FileUploadPolicy.isFileContent(content.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "This content item is not a file upload");
        }
    }

    private LessonContentDtos.Response response(LessonContent content) {
        return new LessonContentDtos.Response(content.getId(), content.getLesson().getId(), content.getContentType(),
                content.getTitle(), content.getTextContent(), content.getExternalUrl(), content.getFileName(),
                content.getMimeType(), content.getFileSize(), content.getUploadStatus(), content.getPosition(),
                content.getVersionNumber(), content.getCreatedAt(), content.getUpdatedAt());
    }
}
