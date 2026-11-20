package com.oep.corelms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.oep.corelms.service.AiTutorProxyService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public M7 routes. The private M7 service never receives the browser JWT. */
@RestController
@RequestMapping("/api/courses/{courseId}/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorController {

    private final AiTutorProxyService proxy;

    @GetMapping("/status")
    public ResponseEntity<byte[]> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return proxy.status(userId(jwt), role(jwt), courseId, requestId(requestId));
    }

    @PostMapping("/conversations")
    public ResponseEntity<byte[]> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                         @RequestBody JsonNode body) {
        requireObject(body, "title");
        String title = null;
        if (body.has("title")) {
            JsonNode value = body.get("title");
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) {
                throw invalid("title must contain 1 to 200 characters");
            }
            title = value.asText();
        }
        return proxy.createConversation(userId(jwt), role(jwt), courseId, requestId(requestId), title);
    }

    @GetMapping("/conversations")
    public ResponseEntity<byte[]> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                       @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        requirePage(page, pageSize);
        return proxy.listConversations(userId(jwt), role(jwt), courseId, requestId(requestId), page, pageSize);
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                      @PathVariable UUID conversationId,
                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return proxy.getConversation(userId(jwt), role(jwt), courseId, requestId(requestId), conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<byte[]> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                         @PathVariable UUID conversationId,
                                         @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        return proxy.deleteConversation(userId(jwt), role(jwt), courseId, requestId(requestId), conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<byte[]> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                           @PathVariable UUID conversationId,
                                           @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        requirePage(page, pageSize);
        return proxy.listMessages(userId(jwt), role(jwt), courseId, requestId(requestId),
                conversationId, page, pageSize);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<byte[]> send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                       @PathVariable UUID conversationId,
                                       @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @RequestBody JsonNode body) {
        requireObject(body, "message");
        if (!body.has("message") || !body.get("message").isTextual()
                || body.get("message").asText().isBlank() || body.get("message").asText().length() > 4000) {
            throw invalid("message must contain 1 to 4000 characters");
        }
        UUID key = parseUuid(idempotencyKey, "Idempotency-Key");
        if (key.version() != 4) {
            throw invalid("Idempotency-Key must be a UUID v4");
        }
        return proxy.sendMessage(userId(jwt), role(jwt), courseId, requestId(requestId),
                conversationId, key, body.get("message").asText());
    }

    private static void requireObject(JsonNode body, String allowedField) {
        if (body == null || !body.isObject() || body.size() > 1
                || (body.size() == 1 && !body.has(allowedField))) {
            throw invalid("Unexpected request fields");
        }
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("page must be positive and page_size must be between 1 and 100");
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }

    private static UUID requestId(String raw) {
        return raw == null ? UUID.randomUUID() : parseUuid(raw, "X-Request-Id");
    }

    private static UUID parseUuid(String raw, String name) {
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return id;
        } catch (IllegalArgumentException ex) {
            throw invalid(name + " must be a UUID");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
