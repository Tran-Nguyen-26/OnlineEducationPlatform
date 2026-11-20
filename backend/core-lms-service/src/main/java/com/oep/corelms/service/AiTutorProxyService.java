package com.oep.corelms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authorized, one-hop public-to-private M7 transport. Never forwards the browser JWT. */
@Service
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorProxyService {

    private static final String ROOT = "/internal/v1/ai/courses/";
    private final AiTutorAccessPolicy accessPolicy;
    private final AiTutorAssertionIssuer issuer;
    private final RestClient client;
    private final ObjectMapper mapper;

    @Autowired
    public AiTutorProxyService(AiTutorAccessPolicy accessPolicy, AiTutorAssertionIssuer issuer,
                               RestClient.Builder builder, ObjectMapper mapper,
                               @Value("${oep.ai-tutor.base-url:}") String baseUrl) {
        this(accessPolicy, issuer, configuredClient(builder, baseUrl), mapper);
    }

    AiTutorProxyService(AiTutorAccessPolicy accessPolicy, AiTutorAssertionIssuer issuer,
                        RestClient client, ObjectMapper mapper) {
        this.accessPolicy = accessPolicy;
        this.issuer = issuer;
        this.mapper = mapper;
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

    public ResponseEntity<byte[]> status(UUID userId, String role, UUID courseId, UUID requestId) {
        return forward(userId, role, courseId, requestId, HttpMethod.GET, "status", null);
    }

    public ResponseEntity<byte[]> createConversation(UUID userId, String role, UUID courseId,
                                                      UUID requestId, String title) {
        return forward(userId, role, courseId, requestId, HttpMethod.POST, "conversations",
                title == null ? Map.of() : Map.of("title", title));
    }

    public ResponseEntity<byte[]> listConversations(UUID userId, String role, UUID courseId,
                                                    UUID requestId, int page, int pageSize) {
        return forward(userId, role, courseId, requestId, HttpMethod.GET,
                "conversations?page=" + page + "&page_size=" + pageSize, null);
    }

    public ResponseEntity<byte[]> getConversation(UUID userId, String role, UUID courseId,
                                                   UUID requestId, UUID conversationId) {
        return forward(userId, role, courseId, requestId, HttpMethod.GET,
                "conversations/" + conversationId, null);
    }

    public ResponseEntity<byte[]> deleteConversation(UUID userId, String role, UUID courseId,
                                                      UUID requestId, UUID conversationId) {
        return forward(userId, role, courseId, requestId, HttpMethod.DELETE,
                "conversations/" + conversationId, null);
    }

    public ResponseEntity<byte[]> listMessages(UUID userId, String role, UUID courseId,
                                               UUID requestId, UUID conversationId, int page, int pageSize) {
        return forward(userId, role, courseId, requestId, HttpMethod.GET,
                "conversations/" + conversationId + "/messages?page=" + page + "&page_size=" + pageSize,
                null);
    }

    public ResponseEntity<byte[]> sendMessage(UUID userId, String role, UUID courseId, UUID requestId,
                                               UUID conversationId, UUID idempotencyKey, String message) {
        AiTutorAccessPolicy.AuthorizedLessonScope scope =
                accessPolicy.requireAuthorizedLessonScope(userId, role, courseId);
        List<UUID> lessonIds = scope.lessonIds();
        String assertion = issuer.issueStudentWithLessonScope(userId, courseId, requestId, lessonIds);
        return execute(courseId, requestId, HttpMethod.POST,
                "conversations/" + conversationId + "/messages",
                Map.of("message", message, "readable_lesson_ids", lessonIds), idempotencyKey, assertion);
    }

    private ResponseEntity<byte[]> forward(UUID userId, String role, UUID courseId, UUID requestId,
                                           HttpMethod method, String suffix, Object body) {
        accessPolicy.requireCourseAccess(userId, role, courseId);
        String assertion = issuer.issueStudent(userId, courseId, requestId);
        return execute(courseId, requestId, method, suffix, body, null, assertion);
    }

    private ResponseEntity<byte[]> execute(UUID courseId, UUID requestId, HttpMethod method,
                                           String suffix, Object body, UUID idempotencyKey, String assertion) {
        try {
            RestClient.RequestBodySpec request = client.method(method)
                    .uri(ROOT + courseId + "/" + suffix)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + assertion)
                    .header("X-Request-Id", requestId.toString());
            if (idempotencyKey != null) {
                request.header("Idempotency-Key", idempotencyKey.toString());
            }
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            Downstream response = request.exchange((sent, received) ->
                    new Downstream(received.getStatusCode().value(), received.getBody().readAllBytes()));
            if (response.status() >= 200 && response.status() < 300) {
                return ResponseEntity.status(response.status())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", requestId.toString())
                        .body(response.body());
            }
            throw mappedError(response);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE, "AI Tutor is unavailable");
        }
    }

    private BusinessException mappedError(Downstream response) {
        try {
            JsonNode root = mapper.readTree(response.body());
            String code = root.path("error").path("code").asText();
            return switch (code) {
                case "AI_INDEX_NOT_READY" -> expected(response, 409, ErrorCode.AI_INDEX_NOT_READY);
                case "AI_CONVERSATION_NOT_FOUND" -> expected(response, 404, ErrorCode.AI_CONVERSATION_NOT_FOUND);
                case "IDEMPOTENCY_CONFLICT" -> expected(response, 409, ErrorCode.IDEMPOTENCY_CONFLICT);
                case "AI_REQUEST_IN_PROGRESS" -> expected(response, 409, ErrorCode.AI_REQUEST_IN_PROGRESS);
                case "AI_PROVIDER_UNAVAILABLE" -> expected(response, 503, ErrorCode.AI_PROVIDER_UNAVAILABLE);
                default -> new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE, "AI Tutor is unavailable");
            };
        } catch (IOException ex) {
            return new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE, "AI Tutor is unavailable");
        }
    }

    private BusinessException expected(Downstream response, int status, ErrorCode code) {
        return response.status() == status
                ? new BusinessException(code, code.name())
                : new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE, "AI Tutor is unavailable");
    }

    private record Downstream(int status, byte[] body) {
    }
}
