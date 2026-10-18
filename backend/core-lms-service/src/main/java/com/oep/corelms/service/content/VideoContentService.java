package com.oep.corelms.service.content;

import com.oep.corelms.config.MinioConfig;
import com.oep.corelms.dto.request.*;
import com.oep.corelms.dto.response.*;
import com.oep.corelms.repository.*;
import com.oep.corelms.service.media.MediaStorageService;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.*;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.event.VideoUploadedEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoContentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo"
    );

    private final LessonRepository lessonRepository;
    private final LessonContentRepository lessonContentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final VideoProcessingJobRepository videoProcessingJobRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MediaStorageService mediaStorageService;
    private final MinioConfig minioConfig;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public PresignedUploadResponse initUpload(UUID lessonId, UUID instructorId, InitUploadRequest req) {
        validateMimeType(req.mimeType());

        Lesson lesson = lessonRepository.findByIdWithCourse(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bài học"));

        Course course = lesson.getModule().getCourse();
        validateInstructorPermission(course, instructorId);

        User instructor = userRepository.getReferenceById(instructorId);
        int nextPosition = lessonContentRepository.findMaxPositionByLessonId(lessonId) + 1;

        LessonContent content = new LessonContent();
        content.setLesson(lesson);
        content.setContentType(ContentType.VIDEO);
        content.setTitle(req.title() != null && !req.title().isBlank() ? req.title() : req.fileName());
        content.setFileName(req.fileName());
        content.setMimeType(req.mimeType());
        content.setFileSize(req.fileSize());
        content.setUploadStatus(UploadStatus.PENDING);
        content.setPosition(nextPosition);
        content.setCreatedBy(instructor);
        content.setUpdatedBy(instructor);

        content = lessonContentRepository.save(content);

        String objectKey = "uploads/" + instructorId + "/" + content.getId() + "/" + sanitizeFileName(req.fileName());
        content.setObjectKey(objectKey);
        lessonContentRepository.save(content);

        String presignedUrl = mediaStorageService.generatePresignedPutUrl(
                minioConfig.getBucketRaw(),
                objectKey,
                minioConfig.getPresignedPutTtlMinutes()
        );

        Instant expiresAt = Instant.now().plus(minioConfig.getPresignedPutTtlMinutes(), ChronoUnit.MINUTES);

        log.info("Khởi tạo upload video: contentId={}, instructorId={}, objectKey={}",
                content.getId(), instructorId, objectKey);

        return new PresignedUploadResponse(content.getId(), presignedUrl, objectKey, expiresAt);
    }

    @Transactional
    public ConfirmUploadResponse confirmUpload(UUID contentId, UUID instructorId, ConfirmUploadRequest req) {
        LessonContent content = lessonContentRepository.findByIdWithCourse(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nội dung bài học"));

        Course course = content.getLesson().getModule().getCourse();
        validateInstructorPermission(course, instructorId);

        // Idempotent: nếu đã upload hoặc available rồi thì không tạo lại job
        if (content.getUploadStatus() == UploadStatus.UPLOADED || content.getUploadStatus() == UploadStatus.AVAILABLE) {
            log.info("Content {} đã ở trạng thái {}, bỏ qua xác nhận trùng lặp", contentId, content.getUploadStatus());
            return new ConfirmUploadResponse(contentId, content.getUploadStatus(), "Video đã được tiếp nhận trước đó");
        }

        // Verify file tồn tại trên MinIO bucket oep-raw
        String objectKey = req.objectKey();
        if (!mediaStorageService.objectExists(minioConfig.getBucketRaw(), objectKey)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy file video trên storage raw");
        }

        MediaStorageService.ObjectMetadata meta = mediaStorageService.getObjectMetadata(
                minioConfig.getBucketRaw(), objectKey
        );

        content.setUploadStatus(UploadStatus.UPLOADED);
        content.setFileSize(meta.size());
        content.setMimeType(meta.contentType());
        lessonContentRepository.save(content);

        // Tạo job xử lý
        VideoProcessingJob job = new VideoProcessingJob();
        job.setLessonContent(content);
        job.setStatus(VideoJobStatus.QUEUED);
        job.setProgressPct(0);
        job.setRawObjectKey(objectKey);
        job = videoProcessingJobRepository.save(job);

        // Publish event AMQP lên RabbitMQ
        VideoUploadedEvent event = new VideoUploadedEvent(
                contentId,
                job.getId(),
                objectKey,
                meta.contentType(),
                meta.size(),
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                MessagingConstants.EXCHANGE,
                MessagingConstants.RK_VIDEO_UPLOADED,
                event
        );

        log.info("Đã xác nhận upload video và publish event: contentId={}, jobId={}", contentId, job.getId());

        return new ConfirmUploadResponse(contentId, UploadStatus.UPLOADED, "Video đã được tiếp nhận. Đang xử lý...");
    }

    @Transactional
    public InitMultipartResponse initMultipartUpload(UUID lessonId, UUID instructorId, InitMultipartRequest req) {
        validateMimeType(req.mimeType());

        Lesson lesson = lessonRepository.findByIdWithCourse(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bài học"));

        Course course = lesson.getModule().getCourse();
        validateInstructorPermission(course, instructorId);

        User instructor = userRepository.getReferenceById(instructorId);
        int nextPosition = lessonContentRepository.findMaxPositionByLessonId(lessonId) + 1;

        LessonContent content = new LessonContent();
        content.setLesson(lesson);
        content.setContentType(ContentType.VIDEO);
        content.setTitle(req.title() != null && !req.title().isBlank() ? req.title() : req.fileName());
        content.setFileName(req.fileName());
        content.setMimeType(req.mimeType());
        content.setFileSize(req.fileSize());
        content.setUploadStatus(UploadStatus.PENDING);
        content.setPosition(nextPosition);
        content.setCreatedBy(instructor);
        content.setUpdatedBy(instructor);

        content = lessonContentRepository.save(content);

        String objectKey = "uploads/" + instructorId + "/" + content.getId() + "/" + sanitizeFileName(req.fileName());
        content.setObjectKey(objectKey);
        lessonContentRepository.save(content);

        String uploadId = mediaStorageService.initiateMultipartUpload(
                minioConfig.getBucketRaw(),
                objectKey,
                req.mimeType()
        );

        log.info("Khởi tạo multipart upload: contentId={}, uploadId={}, key={}", content.getId(), uploadId, objectKey);

        return new InitMultipartResponse(content.getId(), uploadId, objectKey);
    }

    public PresignedPartResponse presignPartUpload(UUID contentId, UUID instructorId, PresignPartRequest req) {
        LessonContent content = lessonContentRepository.findByIdWithCourse(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nội dung bài học"));

        Course course = content.getLesson().getModule().getCourse();
        validateInstructorPermission(course, instructorId);

        String presignedUrl = mediaStorageService.generatePresignedPartUrl(
                minioConfig.getBucketRaw(),
                req.objectKey(),
                req.uploadId(),
                req.partNumber(),
                minioConfig.getPresignedPutTtlMinutes()
        );

        return new PresignedPartResponse(req.partNumber(), presignedUrl);
    }

    @Transactional
    public ConfirmUploadResponse completeMultipartUpload(UUID contentId, UUID instructorId, CompleteMultipartRequest req) {
        LessonContent content = lessonContentRepository.findByIdWithCourse(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nội dung bài học"));

        Course course = content.getLesson().getModule().getCourse();
        validateInstructorPermission(course, instructorId);

        List<MediaStorageService.PartETag> partETags = req.parts().stream()
                .map(p -> new MediaStorageService.PartETag(p.partNumber(), p.etag()))
                .toList();

        mediaStorageService.completeMultipartUpload(
                minioConfig.getBucketRaw(),
                req.objectKey(),
                req.uploadId(),
                partETags
        );

        // Sau khi hoàn thành multipart, tiến hành confirm và dispatch job
        return confirmUpload(contentId, instructorId, new ConfirmUploadRequest(req.objectKey()));
    }

    @Transactional(readOnly = true)
    public StreamUrlResponse getStreamUrl(UUID contentId, UUID studentId) {
        LessonContent content = lessonContentRepository.findByIdWithCourse(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nội dung bài học"));

        if (content.getUploadStatus() != UploadStatus.AVAILABLE || content.getHlsMasterKey() == null) {
            throw new BusinessException(ErrorCode.CONTENT_NOT_READY, "Video đang được xử lý hoặc chưa sẵn sàng");
        }

        Course course = content.getLesson().getModule().getCourse();

        // Kiểm tra quyền truy cập: sinh viên đã đăng ký HOẶC giảng viên của khóa học
        boolean isInstructor = course.getCreatedBy().getId().equals(studentId)
                || courseInstructorRepository.isInstructorOfCourse(course.getId(), studentId);

        if (!isInstructor) {
            if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED
                    && course.getLifecycleStatus() != CourseLifecycleStatus.ARCHIVED) {
                throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Khóa học chưa được phát hành");
            }

            boolean isEnrolled = enrollmentRepository.existsByCourseIdAndStudentId(course.getId(), studentId);
            if (!isEnrolled) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn chưa đăng ký khóa học này");
            }
        }

        String streamUrl = mediaStorageService.generatePresignedGetUrl(
                minioConfig.getBucketHls(),
                content.getHlsMasterKey(),
                minioConfig.getPresignedGetTtlSeconds()
        );

        String thumbnailUrl = content.getThumbnailKey() != null
                ? mediaStorageService.generatePresignedGetUrl(
                minioConfig.getBucketThumbnails(),
                content.getThumbnailKey(),
                minioConfig.getPresignedGetTtlSeconds())
                : null;

        String spriteSheetUrl = content.getSpriteSheetKey() != null
                ? mediaStorageService.generatePresignedGetUrl(
                minioConfig.getBucketThumbnails(),
                content.getSpriteSheetKey(),
                minioConfig.getPresignedGetTtlSeconds())
                : null;

        Instant expiresAt = Instant.now().plus(minioConfig.getPresignedGetTtlSeconds(), ChronoUnit.SECONDS);

        return new StreamUrlResponse(
                contentId,
                streamUrl,
                thumbnailUrl,
                spriteSheetUrl,
                content.getDurationSeconds(),
                expiresAt
        );
    }

    private void validateMimeType(String mimeType) {
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "Định dạng video không được hỗ trợ. Chấp nhận: mp4, webm, quicktime, avi");
        }
    }

    private void validateInstructorPermission(Course course, UUID instructorId) {
        boolean isOwner = course.getCreatedBy().getId().equals(instructorId);
        boolean isCoInstructor = courseInstructorRepository.isInstructorOfCourse(course.getId(), instructorId);

        if (!isOwner && !isCoInstructor) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền chỉnh sửa khóa học này");
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "video.mp4";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
