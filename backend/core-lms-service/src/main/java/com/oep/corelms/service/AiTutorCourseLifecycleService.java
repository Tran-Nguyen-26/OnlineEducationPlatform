package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CourseResponse;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.enums.CourseLifecycleStatus;
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

/** Commit authoritative M2 publication first, then fence stale M7 access by course version. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorCourseLifecycleService {
    private final AdminCourseService courses;
    private final AiTutorAssertionIssuer issuer;
    private final TransactionTemplate tx;
    private final RestClient client;

    @Autowired
    public AiTutorCourseLifecycleService(AdminCourseService courses,
                                         AiTutorAssertionIssuer issuer,
                                         PlatformTransactionManager txManager,
                                         RestClient.Builder builder,
                                         @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(courses, issuer, txManager, configuredClient(builder, baseUrl));
    }

    AiTutorCourseLifecycleService(AdminCourseService courses, AiTutorAssertionIssuer issuer,
                                  PlatformTransactionManager txManager, RestClient client) {
        this.courses = courses;
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

    public CourseResponse updateStatus(UUID courseId, CourseLifecycleStatus target) {
        CourseResponse response = tx.execute(status -> courses.updateStatus(courseId, target));
        if (response == null || response.version() == null) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "Course version is unavailable for AI Tutor synchronization");
        }
        UUID requestId = UUID.randomUUID();
        try {
            var acknowledgement = client.put()
                    .uri("/internal/v1/ai/courses/{courseId}/access", courseId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + issuer.issueIndexer(requestId))
                    .header("X-Request-Id", requestId.toString())
                    .body(Map.of("course_version", response.version(),
                            "active", response.lifecycleStatus() == CourseLifecycleStatus.PUBLISHED))
                    .retrieve().toBodilessEntity();
            if (acknowledgement.getStatusCode() != HttpStatus.NO_CONTENT) {
                throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                        "AI Tutor did not acknowledge course access state");
            }
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI Tutor could not acknowledge course access state");
        }
        return response;
    }
}
