package com.oep.corelms.service;

import com.oep.corelms.repository.ContentChangeLogRepository;
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

/** Two committed local transactions around M7's synchronous invalidation acknowledgement. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorContentDeletionService {

    private record Scope(UUID courseId, UUID lessonId, int version) {}

    private final LessonContentRepository contents;
    private final ContentChangeLogRepository changeLogs;
    private final AiTutorAssertionIssuer issuer;
    private final TransactionTemplate tx;
    private final RestClient client;

    @Autowired
    public AiTutorContentDeletionService(LessonContentRepository contents,
                                         ContentChangeLogRepository changeLogs,
                                         AiTutorAssertionIssuer issuer,
                                         PlatformTransactionManager txManager,
                                         RestClient.Builder builder,
                                         @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(contents, changeLogs, issuer, txManager, configuredClient(builder, baseUrl));
    }

    AiTutorContentDeletionService(LessonContentRepository contents,
                                  ContentChangeLogRepository changeLogs,
                                  AiTutorAssertionIssuer issuer,
                                  PlatformTransactionManager txManager,
                                  RestClient client) {
        this.contents = contents;
        this.changeLogs = changeLogs;
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

    public void delete(UUID contentId) {
        Scope scope = tx.execute(status -> {
            LessonContent content = contents.findById(contentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
            if (content.getUploadStatus() != UploadStatus.DELETING) {
                content.setUploadStatus(UploadStatus.DELETING);
                contents.saveAndFlush(content);
            }
            return new Scope(content.getLesson().getModule().getCourse().getId(),
                    content.getLesson().getId(), content.getVersionNumber());
        });
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
                throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE, "AI Tutor did not acknowledge invalidation");
            }
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI Tutor could not acknowledge content invalidation");
        }
        tx.executeWithoutResult(status -> {
            LessonContent content = contents.findById(contentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson content not found"));
            if (content.getUploadStatus() != UploadStatus.DELETING
                    || content.getVersionNumber() != scope.version()) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Content changed during invalidation");
            }
            changeLogs.deleteByContent_Id(contentId);
            contents.delete(content);
        });
    }
}
