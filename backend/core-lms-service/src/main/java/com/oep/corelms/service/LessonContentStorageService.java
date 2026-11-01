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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LessonContentStorageService {
    static final int MULTIPART_PART_SIZE_BYTES = 10 * 1024 * 1024;
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
                || content.getFileSize() > FileUploadPolicy.maxFileSizeBytes(content.getContentType())) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "Declared file size exceeds the limit for this content type");
        }
        if (FileUploadPolicy.requiresMultipart(content.getContentType(), content.getFileSize())) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Large video files require multipart upload");
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
    public LessonContentDtos.MultipartUploadResponse issueMultipartUploadUrls(UUID actorId, UUID contentId) {
        LessonContent content = requireContentForUpdate(contentId);
        courses.assertCourseManagerAccess(courseId(content), actorId);
        requireFileContent(content);
        if (content.getContentType() != com.oep.coremodel.enums.ContentType.VIDEO
                || content.getFileSize() == null
                || !FileUploadPolicy.requiresMultipart(content.getContentType(), content.getFileSize())
                || content.getFileSize() > FileUploadPolicy.maxFileSizeBytes(content.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA,
                    "Multipart upload is reserved for VIDEO files larger than 50 MiB and at most 2 GiB");
        }
        if (content.getUploadStatus() != UploadStatus.PENDING && content.getUploadStatus() != UploadStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Content is not waiting for upload");
        }

        if (content.getMultipartUploadId() != null && content.getObjectKey() != null) {
            storage.abortMultipartUpload(content.getObjectKey(), content.getMultipartUploadId());
        }
        String objectKey = content.getObjectKey();
        if (objectKey == null || content.getUploadStatus() == UploadStatus.FAILED) {
            objectKey = "content/" + UUID.randomUUID();
            content.setObjectKey(objectKey);
        }
        String uploadId = storage.createMultipartUpload(objectKey, content.getMimeType());
        content.setMultipartUploadId(uploadId);
        content.setUploadStatus(UploadStatus.PENDING);
        contents.saveAndFlush(content);

        int partCount = (int) ((content.getFileSize() + MULTIPART_PART_SIZE_BYTES - 1) / MULTIPART_PART_SIZE_BYTES);
        Duration ttl = Duration.ofSeconds(properties.getUploadUrlTtlSeconds());
        List<LessonContentDtos.MultipartPartUrl> parts = new ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            parts.add(new LessonContentDtos.MultipartPartUrl(partNumber,
                    storage.presignUploadPart(objectKey, uploadId, partNumber, ttl)));
        }
        return new LessonContentDtos.MultipartUploadResponse(uploadId, MULTIPART_PART_SIZE_BYTES, parts,
                Instant.now().plus(ttl));
    }

    @Transactional
    public LessonContentDtos.Response completeMultipartUpload(UUID actorId, UUID contentId,
                                                               LessonContentDtos.CompleteMultipartRequest request) {
        LessonContent content = requireContentForUpdate(contentId);
        courses.assertCourseManagerAccess(courseId(content), actorId);
        requireFileContent(content);
        String uploadId = content.getMultipartUploadId();
        if (uploadId == null || content.getObjectKey() == null || content.getUploadStatus() != UploadStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Content has no active multipart upload");
        }

        int expectedCount = (int) ((content.getFileSize() + MULTIPART_PART_SIZE_BYTES - 1) / MULTIPART_PART_SIZE_BYTES);
        List<LessonContentDtos.MultipartPart> suppliedParts = request.parts();
        Set<Integer> partNumbers = new HashSet<>();
        if (suppliedParts.size() != expectedCount
                || suppliedParts.stream().anyMatch(part -> part.partNumber() < 1
                || part.partNumber() > expectedCount || !partNumbers.add(part.partNumber()))
                || partNumbers.size() != expectedCount) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA,
                    "Multipart completion must include each expected part exactly once");
        }

        List<ObjectStorageService.CompletedPartData> completedParts = suppliedParts.stream()
                .sorted(java.util.Comparator.comparingInt(LessonContentDtos.MultipartPart::partNumber))
                .map(part -> new ObjectStorageService.CompletedPartData(part.partNumber(), part.eTag()))
                .toList();
        storage.completeMultipartUpload(content.getObjectKey(), uploadId, completedParts);
        ObjectStorageService.ObjectMetadata metadata = storage.stat(content.getObjectKey());
        validateUploadedMetadata(content, metadata);
        content.setUploadStatus(UploadStatus.UPLOADED);
        content.setMultipartUploadId(null);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        return response(contents.saveAndFlush(content));
    }

    @Transactional
    public void cancelMultipartUpload(UUID actorId, UUID contentId) {
        LessonContent content = requireContentForUpdate(contentId);
        courses.assertCourseManagerAccess(courseId(content), actorId);
        requireFileContent(content);
        if (content.getMultipartUploadId() == null || content.getObjectKey() == null) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Content has no active multipart upload");
        }
        storage.abortMultipartUpload(content.getObjectKey(), content.getMultipartUploadId());
        content.setMultipartUploadId(null);
        content.setObjectKey(null);
        content.setUploadStatus(UploadStatus.FAILED);
        contents.saveAndFlush(content);
    }

    @Transactional
    public void failUpload(UUID actorId, UUID contentId) {
        LessonContent content = requireContentForUpdate(contentId);
        courses.assertCourseManagerAccess(courseId(content), actorId);
        requireFileContent(content);
        if (content.getUploadStatus() == UploadStatus.FAILED) return;
        if (content.getUploadStatus() != UploadStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_UPLOAD_STATE, "Only a pending upload can be marked failed");
        }
        if (content.getMultipartUploadId() != null && content.getObjectKey() != null) {
            storage.abortMultipartUpload(content.getObjectKey(), content.getMultipartUploadId());
        }
        if (content.getObjectKey() != null) storage.delete(content.getObjectKey());
        content.setMultipartUploadId(null);
        content.setObjectKey(null);
        content.setUploadStatus(UploadStatus.FAILED);
        contents.saveAndFlush(content);
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
        try {
            validateUploadedMetadata(content, metadata);
        } catch (BusinessException error) {
            storage.delete(content.getObjectKey());
            throw error;
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

    private void validateUploadedMetadata(LessonContent content, ObjectStorageService.ObjectMetadata metadata) {
        if (metadata.size() > FileUploadPolicy.maxFileSizeBytes(content.getContentType())) {
            throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "Uploaded file exceeds the limit for this content type");
        }
        if (content.getFileSize() == null || metadata.size() != content.getFileSize()
                || metadata.contentType() == null || !metadata.contentType().equalsIgnoreCase(content.getMimeType())) {
            throw new BusinessException(ErrorCode.UPLOAD_METADATA_MISMATCH,
                    "Uploaded object size or Content-Type does not match the declared file metadata");
        }
    }

    private LessonContentDtos.Response response(LessonContent content) {
        return new LessonContentDtos.Response(content.getId(), content.getLesson().getId(), content.getContentType(),
                content.getTitle(), content.getTextContent(), content.getExternalUrl(), content.getFileName(),
                content.getMimeType(), content.getFileSize(), content.getUploadStatus(), content.getPosition(),
                content.getVersionNumber(), content.getCreatedAt(), content.getUpdatedAt(), content.getDurationSeconds());
    }
}
