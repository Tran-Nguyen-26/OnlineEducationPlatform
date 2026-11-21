package com.oep.corelms.service;

import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.UpdateLessonContentRequest;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
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

/** Hide an indexed revision before mutating source text or citation metadata. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorContentUpdateService {
    private record Scope(UUID courseId, UUID lessonId, int version) {}
    private record Prepared(Scope scope, LessonContentResponse response) {}

    private final LessonContentRepository contents;
    private final AdminLessonContentService contentService;
    private final ContentChangeLogService changeLog;
    private final AiTutorIndexOutbox outbox;
    private final AiTutorAssertionIssuer issuer;
    private final TransactionTemplate tx;
    private final RestClient client;

    @Autowired
    public AiTutorContentUpdateService(LessonContentRepository contents,
                                       AdminLessonContentService contentService,
                                       ContentChangeLogService changeLog,
                                       AiTutorIndexOutbox outbox,
                                       AiTutorAssertionIssuer issuer,
                                       PlatformTransactionManager txManager,
                                       RestClient.Builder builder,
                                       @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(contents, contentService, changeLog, outbox, issuer, txManager,
                configuredClient(builder, baseUrl));
    }

    AiTutorContentUpdateService(LessonContentRepository contents,
                                AdminLessonContentService contentService,
                                ContentChangeLogService changeLog,
                                AiTutorIndexOutbox outbox,
                                AiTutorAssertionIssuer issuer,
                                PlatformTransactionManager txManager,
                                RestClient client) {
        this.contents = contents;
        this.contentService = contentService;
        this.changeLog = changeLog;
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

    public LessonContentResponse update(UUID contentId, UpdateLessonContentRequest request, UUID adminId) {
        Prepared prepared = tx.execute(status -> {
            LessonContent content = locked(contentId);
            if (content.getUploadStatus() == UploadStatus.DELETING) {
                throw invalidState("Content deletion is pending AI Tutor invalidation");
            }
            boolean pending = content.getUploadStatus() == UploadStatus.INVALIDATING;
            // A draft/archived course may still have an older indexed revision from a prior publication.
            boolean indexed = content.getUploadStatus() == UploadStatus.AVAILABLE
                    && AiTutorIndexOutbox.eligibleType(content);
            if (!pending && !indexed) {
                contentService.update(contentId, request);
                changeLog.record(contentId, adminId, "UPDATED");
                return new Prepared(null, LessonContentResponse.from(content));
            }
            validateText(content, request);
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
                        "AI Tutor did not acknowledge source invalidation");
            }
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI Tutor could not acknowledge source invalidation");
        }
        return tx.execute(status -> {
            LessonContent content = locked(contentId);
            if (content.getUploadStatus() != UploadStatus.INVALIDATING
                    || content.getVersionNumber() != scope.version()) {
                throw invalidState("Content changed during AI Tutor invalidation");
            }
            content.setTitle(request.title());
            content.setPosition(request.position());
            if (content.getContentType() == ContentType.TEXT) content.setTextContent(request.textContent());
            if (content.getContentType() == ContentType.LINK) content.setExternalUrl(request.externalUrl());
            content.setUploadStatus(UploadStatus.AVAILABLE);
            contents.saveAndFlush(content);
            changeLog.record(contentId, adminId, "UPDATED");
            outbox.publishIfEligible(content);
            return LessonContentResponse.from(content);
        });
    }

    private LessonContent locked(UUID contentId) {
        return contents.findByIdForUpdate(contentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
    }

    private static void validateText(LessonContent content, UpdateLessonContentRequest request) {
        if (content.getContentType() == ContentType.TEXT
                && (request.textContent() == null || request.textContent().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "textContent is required for TEXT content");
        }
    }

    private static BusinessException invalidState(String message) {
        return new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, message);
    }
}
