package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.dto.admin.UploadUrlResponse;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/** Invalidates an old file before issuing a replacement upload grant. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorFileReplacementService {
    private record Scope(UUID courseId, UUID lessonId, int version) {}
    private record Prepared(Scope scope, UploadUrlResponse response) {}

    private final LessonContentRepository contents;
    private final AdminLessonContentService legacy;
    private final ContentChangeLogService logs;
    private final AiTutorIndexOutbox outbox;
    private final AiTutorAssertionIssuer issuer;
    private final TransactionTemplate tx;
    private final RestClient client;

    @Autowired
    public AiTutorFileReplacementService(LessonContentRepository contents,
                                         AdminLessonContentService legacy,
                                         ContentChangeLogService logs,
                                         AiTutorIndexOutbox outbox,
                                         AiTutorAssertionIssuer issuer,
                                         PlatformTransactionManager txManager,
                                         RestClient.Builder builder,
                                         @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(contents, legacy, logs, outbox, issuer, txManager,
                configuredClient(builder, baseUrl));
    }

    AiTutorFileReplacementService(LessonContentRepository contents,
                                  AdminLessonContentService legacy,
                                  ContentChangeLogService logs,
                                  AiTutorIndexOutbox outbox,
                                  AiTutorAssertionIssuer issuer,
                                  PlatformTransactionManager txManager,
                                  RestClient client) {
        this.contents = contents;
        this.legacy = legacy;
        this.logs = logs;
        this.outbox = outbox;
        this.issuer = issuer;
        this.tx = new TransactionTemplate(txManager);
        this.client = client;
    }

    private static RestClient configuredClient(RestClient.Builder builder, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI_TUTOR_BASE_URL is required when AI Tutor is enabled");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(10_000);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public UploadUrlResponse requestUploadUrl(UUID contentId, RequestUploadUrlRequest request) {
        Prepared prepared = tx.execute(status -> {
            LessonContent content = locked(contentId);
            if (content.getUploadStatus() == UploadStatus.DELETING) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Content deletion is pending AI Tutor invalidation");
            }
            if (!AdminLessonContentService.isFileBasedType(content.getContentType())) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "This content type does not take a file upload");
            }
            // Validate before hiding a readable source. The legacy upload path repeats this check.
            AiTutorLearningFilePolicy.maximumBytes(content.getContentType(),
                    request.fileName(), request.mimeType());
            boolean pending = content.getUploadStatus() == UploadStatus.INVALIDATING;
            boolean indexed = content.getUploadStatus() == UploadStatus.AVAILABLE
                    && AiTutorIndexOutbox.eligibleType(content);
            if (!pending && !indexed) {
                return new Prepared(null, legacy.requestUploadUrl(contentId, request));
            }
            if (!pending) {
                content.setUploadStatus(UploadStatus.INVALIDATING);
                contents.saveAndFlush(content);
            }
            return new Prepared(new Scope(content.getLesson().getModule().getCourse().getId(),
                    content.getLesson().getId(), content.getVersionNumber()), null);
        });
        if (prepared.scope() == null) return prepared.response();
        Scope scope = prepared.scope();
        UUID requestId = UUID.randomUUID();
        try {
            var response = client.post()
                    .uri("/internal/v1/ai/sources/{contentId}/deactivate", contentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + issuer.issueIndexer(requestId))
                    .header("X-Request-Id", requestId.toString())
                    .body(Map.of("course_id", scope.courseId(), "lesson_id", scope.lessonId(),
                            "version_number", scope.version()))
                    .retrieve().toBodilessEntity();
            if (response.getStatusCode() != HttpStatus.NO_CONTENT) {
                throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                        "AI Tutor did not acknowledge file invalidation");
            }
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI Tutor could not acknowledge file invalidation");
        }
        return tx.execute(status -> {
            LessonContent content = locked(contentId);
            if (content.getUploadStatus() != UploadStatus.INVALIDATING
                    || content.getVersionNumber() != scope.version()) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Content changed during AI Tutor invalidation");
            }
            // The upload grant moves the row to PENDING within this transaction.
            content.setUploadStatus(UploadStatus.AVAILABLE);
            return legacy.requestUploadUrl(contentId, request);
        });
    }

    public LessonContentResponse completeUpload(UUID contentId, CompleteUploadRequest request, UUID adminId) {
        return tx.execute(status -> {
            LessonContent content = locked(contentId);
            if (content.getUploadStatus() != UploadStatus.PENDING) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "An upload grant must precede file completion");
            }
            boolean initial = content.getFileSize() == null;
            legacy.completeUpload(contentId, request);
            if (initial) {
                // The legacy completion emitted version 1 in this same transaction.
                logs.recordCurrentVersion(contentId, adminId, "FILE_REPLACED");
            } else {
                logs.record(contentId, adminId, "FILE_REPLACED");
                outbox.publishIfEligible(content);
            }
            return LessonContentResponse.from(content);
        });
    }

    private LessonContent locked(UUID contentId) {
        return contents.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
    }
}
