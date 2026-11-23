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

    static boolean isFileBasedType(ContentType type) {
        return FILE_BASED_TYPES.contains(type);
    }

    private final TranscodeRequester transcodeRequester;
    private final LessonRepository lessons;
    private final LessonContentRepository contents;
    private final UserRepository users;
    private final StorageService storageService;
    private final com.oep.corelms.repository.ContentChangeLogRepository changeLogs;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AiTutorIndexOutbox aiIndexOutbox;

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

        content = contents.saveAndFlush(content);
        if (aiIndexOutbox != null) aiIndexOutbox.publishIfEligible(content);
        return LessonContentResponse.from(content);
    }

    @Transactional(readOnly = true)
    public List<LessonContentResponse> list(UUID lessonId) {
        return contents.findByLesson_IdOrderByPositionAsc(lessonId).stream().map(LessonContentResponse::from).toList();
    }

    @Transactional
    public LessonContentResponse update(UUID contentId, UpdateLessonContentRequest request) {
        LessonContent content = findOrThrow(contentId);
        requireNotDeleting(content);
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
        requireNotDeleting(content);
        if (!FILE_BASED_TYPES.contains(content.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "This content type does not take a file upload");
        }

        AiTutorLearningFilePolicy.maximumBytes(content.getContentType(), request.fileName(), request.mimeType());
        if (request.fileSize() != null) {
            requireWithinLimit(content.getContentType(), request.fileName(), request.fileSize());
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
        requireNotDeleting(content);
        boolean initialUpload = content.getFileSize() == null;
        if (content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "No upload was requested for this content");
        }
        long maximumBytes = AiTutorLearningFilePolicy.maximumBytes(
                content.getContentType(), content.getFileName(), content.getMimeType());
        if (maximumBytes > 0) {
            StorageService.StoredObjectInfo stored = storageService.inspectObject(content.getObjectKey());
            if (stored.sizeBytes() != request.fileSize()
                    || !AiTutorLearningFilePolicy.baseMime(stored.mimeType()).equals(
                    AiTutorLearningFilePolicy.baseMime(content.getMimeType()))) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Uploaded file size or MIME type does not match the confirmed learning content");
            }
            if (stored.sizeBytes() > maximumBytes) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Learning content exceeds the allowed upload size");
            }
        }
        Long actual = storageService.objectSize(content.getObjectKey());
        long size = actual != null && actual > 0 ? actual : request.fileSize();
        try {
            requireWithinLimit(content.getContentType(), content.getFileName(), size);
        } catch (BusinessException tooLarge) {
            storageService.deleteObject(content.getObjectKey());
            throw tooLarge;
        }
        content.setFileSize(size);
        content.setDurationSeconds(request.durationSeconds());
        content.setUploadStatus(UploadStatus.AVAILABLE);
        transcodeRequester.request(content);   // VIDEO only: adaptive HLS version is prepared in the background
        content = contents.saveAndFlush(content);
        if (initialUpload && aiIndexOutbox != null) aiIndexOutbox.publishIfEligible(content);
        return LessonContentResponse.from(content);
    }

    /** Re-runs HLS preparation (e.g. after a FAILED transcode). */
    @Transactional
    public LessonContentResponse retranscode(UUID contentId) {
        LessonContent content = findOrThrow(contentId);
        requireNotDeleting(content);
        if (!transcodeRequester.request(content)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Only uploaded videos can be transcoded");
        }
        return LessonContentResponse.from(contents.saveAndFlush(content));
    }

    // BRD v1.3 upload limits: video 2 GB, subtitles (.vtt/.srt) 5 MB, documents (PDF/DOCX/...) 20 MB.
    static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
    static final long MAX_SUBTITLE_BYTES = 5L * 1024 * 1024;
    static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;

    private void requireWithinLimit(ContentType type, String fileName, long bytes) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        long max;
        String label;
        if (name.endsWith(".vtt") || name.endsWith(".srt")) { max = MAX_SUBTITLE_BYTES; label = "phụ đề (.vtt/.srt) tối đa 5 MB"; }
        else if (type == ContentType.VIDEO) { max = MAX_VIDEO_BYTES; label = "video tối đa 2 GB"; }
        else if (type == ContentType.PDF || type == ContentType.DOCUMENT || type == ContentType.SLIDE
                || name.endsWith(".pdf") || name.endsWith(".docx")) { max = MAX_DOCUMENT_BYTES; label = "tài liệu tối đa 20 MB"; }
        else { return; }   // other FILE contents keep their previous behaviour
        if (bytes > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tệp quá lớn: " + label);
        }
    }

    private LessonContent findOrThrow(UUID contentId) {
        return contents.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
    }

    private static void requireNotDeleting(LessonContent content) {
        if (content.getUploadStatus() == UploadStatus.DELETING
                || content.getUploadStatus() == UploadStatus.INVALIDATING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Content is pending AI Tutor invalidation");
        }
    }
}
