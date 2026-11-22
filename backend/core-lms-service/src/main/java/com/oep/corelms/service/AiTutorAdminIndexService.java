package com.oep.corelms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/** Admin reads M7 processing metadata through M2, never via a student M7 route. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorAdminIndexService {
    private final AdminCourseService courses;
    private final AiTutorAssertionIssuer issuer;
    private final RestClient client;

    @Autowired
    public AiTutorAdminIndexService(AdminCourseService courses, AiTutorAssertionIssuer issuer,
                                    RestClient.Builder builder,
                                    @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(courses, issuer, configuredClient(builder, baseUrl));
    }

    AiTutorAdminIndexService(AdminCourseService courses, AiTutorAssertionIssuer issuer,
                             RestClient client) {
        this.courses = courses;
        this.issuer = issuer;
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

    public JsonNode status(UUID courseId) {
        courses.get(courseId);
        UUID requestId = UUID.randomUUID();
        try {
            JsonNode body = client.get()
                    .uri("/internal/v1/ai/courses/{courseId}/index-status", courseId)
                    .header("Authorization", "Bearer " + issuer.issueIndexer(requestId))
                    .header("X-Request-Id", requestId.toString())
                    .retrieve().body(JsonNode.class);
            if (body == null || !courseId.toString().equals(body.path("course_id").asText())
                    || !body.path("items").isArray()) {
                throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                        "AI Tutor returned an invalid index status");
            }
            return body;
        } catch (RestClientException exception) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                    "AI Tutor index status is unavailable");
        }
    }
}
