package com.oep.corelms.service;

import com.oep.corelms.config.MediaStorageConfig;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LearningOutcomeRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oep.media.endpoint")
public class LessonMediaUploadService {
    private static final Duration LINK_DURATION = Duration.ofMinutes(15);
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final LessonContentRepository contents;
    private final LearningOutcomeRepository outcomes;
    private final UserRepository users;
    private final S3Client storage;
    private final S3Presigner signer;
    private final MediaStorageConfig.Properties properties;

    public record UploadRequest(@NotBlank @Size(max = 255) String filename, @Positive long size,
                                @NotNull UUID outcomeId) {}
    public record UploadTicket(UUID contentId, String url, Map<String, java.util.List<String>> headers) {}
    public record Confirmation(UUID contentId, UploadStatus status) {}

    @Transactional
    public UploadTicket initiate(UUID courseId, UUID lessonId, UUID actor, UploadRequest upload) {
        Format format = Format.of(upload.filename());
        if (upload.size() < 1 || upload.size() > format.limit) throw invalid("Kích thước tệp không hợp lệ");
        requireDraft(courseId);
        var lesson = lessons.findById(lessonId).filter(l -> l.getModule().getCourse().getId().equals(courseId))
                .orElseThrow(this::missing);
        lesson = lessons.findByIdAndModuleIdForUpdate(lessonId, lesson.getModule().getId()).orElseThrow(this::missing);
        var outcome = outcomes.findByIdAndCourseId(upload.outcomeId(), courseId).orElseThrow(this::missing);
        var owner = users.findById(actor).orElseThrow(this::missing);
        String key = "incoming/" + courseId + "/" + UUID.randomUUID() + "." + format.name().toLowerCase(Locale.ROOT);

        LessonContent item = new LessonContent();
        item.setLesson(lesson);
        item.setLearningOutcome(outcome);
        item.setContentType(format.type);
        item.setObjectKey(key);
        item.setFileName(upload.filename());
        item.setMimeType(format.mime);
        item.setFileSize(upload.size());
        item.setCreatedBy(owner);
        item.setPosition(contents.findByLessonIdOrderByPositionAsc(lessonId).stream()
                .mapToInt(LessonContent::getPosition).max().orElse(-1) + 1);
        contents.saveAndFlush(item);
        var signed = signer.presignPutObject(request -> request.signatureDuration(LINK_DURATION)
                .putObjectRequest(put -> put.bucket(properties.bucket()).key(key)
                        .contentType(format.mime).contentLength(upload.size())));
        return new UploadTicket(item.getId(), signed.url().toExternalForm(), signed.signedHeaders());
    }

    @Transactional
    public Confirmation confirm(UUID courseId, UUID lessonId, UUID contentId) {
        requireDraft(courseId);
        var item = contents.findByIdAndLessonIdForUpdate(contentId, lessonId)
                .filter(c -> c.getLesson().getModule().getCourse().getId().equals(courseId))
                .filter(c -> c.getObjectKey() != null && c.getObjectKey().startsWith("incoming/" + courseId + "/"))
                .orElseThrow(this::missing);
        if (item.getUploadStatus() != UploadStatus.PENDING) return new Confirmation(contentId, item.getUploadStatus());
        try {
            var head = storage.headObject(request -> request.bucket(properties.bucket()).key(item.getObjectKey()));
            if (!item.getFileSize().equals(head.contentLength()) || !item.getMimeType().equals(head.contentType())) {
                throw invalid("Tệp tải lên không khớp thông tin đã đăng ký");
            }
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) throw invalid("Tệp chưa được tải lên");
            throw ex;
        }
        item.setUploadStatus(UploadStatus.UPLOADED);
        return new Confirmation(contentId, UploadStatus.UPLOADED);
    }

    private void requireDraft(UUID courseId) {
        var course = courses.findByIdForUpdate(courseId).orElseThrow(this::missing);
        if (course.getLifecycleStatus() != CourseLifecycleStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Chỉ có thể tải học liệu cho bản nháp");
        }
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy mục trong khóa học");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    enum Format {
        MP4(ContentType.VIDEO, "video/mp4", 2L * 1024 * 1024 * 1024),
        PDF(ContentType.PDF, "application/pdf", 20L * 1024 * 1024),
        DOCX(ContentType.DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 20L * 1024 * 1024),
        VTT(ContentType.FILE, "text/vtt", 5L * 1024 * 1024),
        SRT(ContentType.FILE, "application/x-subrip", 5L * 1024 * 1024);

        private final ContentType type;
        private final String mime;
        private final long limit;

        Format(ContentType type, String mime, long limit) {
            this.type = type;
            this.mime = mime;
            this.limit = limit;
        }

        static Format of(String filename) {
            int dot = filename.lastIndexOf('.');
            if (dot < 1) throw invalid("Định dạng học liệu không được hỗ trợ");
            try {
                return valueOf(filename.substring(dot + 1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw invalid("Định dạng học liệu không được hỗ trợ");
            }
        }
    }
}
