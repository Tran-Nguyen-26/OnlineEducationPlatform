package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.CreateLessonContentRequest;
import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.dto.admin.UpdateLessonContentRequest;
import com.oep.corelms.dto.admin.UploadUrlResponse;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Video/tài liệu go through requestUploadUrl -> browser PUTs to MinIO -> completeUpload; TEXT/LINK are set directly. */
@Service
@RequiredArgsConstructor
public class AdminLessonContentService {

    private static final Set<ContentType> FILE_BASED_TYPES =
            EnumSet.of(ContentType.VIDEO, ContentType.PDF, ContentType.SLIDE, ContentType.DOCUMENT, ContentType.FILE);

    private final LessonRepository lessons;
    private final LessonContentRepository contents;
    private final UserRepository users;
    private final StorageService storageService;
    private final com.oep.corelms.repository.ContentChangeLogRepository changeLogs;

    @Value("${oep.storage.upload-url-ttl-seconds:900}")
    private long uploadUrlTtlSeconds;

    @Transactional
    public LessonContentResponse create(UUID lessonId, UUID adminId, CreateLessonContentRequest request) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));

        LessonContent content = new LessonContent();
        content.setLesson(lesson);
        content.setContentType(request.contentType());
        content.setTitle(request.title());
        content.setPosition(request.position());
        content.setCreatedBy(users.getReferenceById(adminId));

        if (FILE_BASED_TYPES.contains(request.contentType())) {
            content.setUploadStatus(UploadStatus.PENDING);
        } else if (request.contentType() == ContentType.TEXT) {
            if (request.textContent() == null || request.textContent().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "textContent is required for TEXT content");
            }
            content.setTextContent(request.textContent());
            content.setUploadStatus(UploadStatus.AVAILABLE);
        } else if (request.contentType() == ContentType.LINK) {
            if (request.externalUrl() == null || request.externalUrl().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "externalUrl is required for LINK content");
            }
            content.setExternalUrl(request.externalUrl());
            content.setUploadStatus(UploadStatus.AVAILABLE);
        }

        return LessonContentResponse.from(contents.saveAndFlush(content));
    }

    @Transactional(readOnly = true)
    public List<LessonContentResponse> list(UUID lessonId) {
        return contents.findByLesson_IdOrderByPositionAsc(lessonId).stream().map(LessonContentResponse::from).toList();
    }

    @Transactional
    public LessonContentResponse update(UUID contentId, UpdateLessonContentRequest request) {
        LessonContent content = findOrThrow(contentId);
        content.setTitle(request.title());
        content.setPosition(request.position());
        if (content.getContentType() == ContentType.TEXT) {
            content.setTextContent(request.textContent());
        } else if (content.getContentType() == ContentType.LINK) {
            content.setExternalUrl(request.externalUrl());
        }
        return LessonContentResponse.from(contents.saveAndFlush(content));
    }

    @Transactional
    public void delete(UUID contentId) {
        findOrThrow(contentId);
        changeLogs.deleteByContent_Id(contentId);
        contents.deleteById(contentId);
    }

    @Transactional
    public UploadUrlResponse requestUploadUrl(UUID contentId, RequestUploadUrlRequest request) {
        LessonContent content = findOrThrow(contentId);
        if (!FILE_BASED_TYPES.contains(content.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "This content type does not take a file upload");
        }

        String objectKey = storageService.newObjectKey(request.fileName());
        content.setObjectKey(objectKey);
        content.setFileName(request.fileName());
        content.setMimeType(request.mimeType());
        content.setUploadStatus(UploadStatus.PENDING);
        contents.save(content);

        String uploadUrl = storageService.presignUpload(objectKey, request.mimeType());
        return new UploadUrlResponse(uploadUrl, objectKey, uploadUrlTtlSeconds);
    }

    @Transactional
    public LessonContentResponse completeUpload(UUID contentId, CompleteUploadRequest request) {
        LessonContent content = findOrThrow(contentId);
        if (content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "No upload was requested for this content");
        }
        content.setFileSize(request.fileSize());
        content.setDurationSeconds(request.durationSeconds());
        content.setUploadStatus(UploadStatus.AVAILABLE);
        return LessonContentResponse.from(contents.saveAndFlush(content));
    }

    private LessonContent findOrThrow(UUID contentId) {
        return contents.findById(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
    }
}
