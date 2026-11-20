package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorProxyServiceTest {

    private AiTutorAccessPolicy policy;
    private AiTutorAssertionIssuer issuer;
    private AiTutorProxyService proxy;
    private MockRestServiceServer server;
    private UUID userId;
    private UUID courseId;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        policy = mock(AiTutorAccessPolicy.class);
        issuer = new AiTutorAssertionIssuer("test-only-service-secret-with-at-least-32-bytes");
        RestClient.Builder builder = RestClient.builder().baseUrl("http://m7.internal:8000");
        server = MockRestServiceServer.bindTo(builder).build();
        proxy = new AiTutorProxyService(policy, issuer, builder.build(), new ObjectMapper());
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        requestId = UUID.randomUUID();
    }

    @Test
    void statusChecksAccessThenUsesOnlyInternalAssertion() throws Exception {
        String path = "http://m7.internal:8000/internal/v1/ai/courses/" + courseId + "/status";
        server.expect(once(), requestTo(path))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Request-Id", requestId.toString()))
                .andExpect(request -> {
                    String authorization = request.getHeaders().getFirst("Authorization");
                    assertTrue(authorization.startsWith("Bearer "));
                    JWTClaimsSet claims = parseClaims(authorization);
                    assertEquals(userId.toString(), claims.getSubject());
                    assertEquals(courseId.toString(), claim(claims, "course_id"));
                    assertEquals(requestId.toString(), claim(claims, "request_id"));
                })
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"READY\"}"));

        var response = proxy.status(userId, "STUDENT", courseId, requestId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"status\":\"READY\"}", new String(response.getBody()));
        assertEquals(requestId.toString(), response.getHeaders().getFirst("X-Request-Id"));
        verify(policy).requireCourseAccess(userId, "STUDENT", courseId);
        server.verify();
    }

    @Test
    void sendBindsScopeToPrivateBodyAndForwardsIdempotencyKey() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(policy.requireAuthorizedLessonScope(userId, "STUDENT", courseId))
                .thenReturn(new AiTutorAccessPolicy.AuthorizedLessonScope(userId, courseId, List.of(lessonId)));
        String path = "http://m7.internal:8000/internal/v1/ai/courses/" + courseId
                + "/conversations/" + conversationId + "/messages";
        server.expect(once(), requestTo(path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", key.toString()))
                .andExpect(header("X-Request-Id", requestId.toString()))
                .andExpect(content().json("{\"message\":\"Question\",\"readable_lesson_ids\":[\"" + lessonId + "\"]}"))
                .andExpect(request -> {
                    JWTClaimsSet claims = parseClaims(request.getHeaders().getFirst("Authorization"));
                    assertEquals(com.oep.corelms.security.AiTutorLessonScope.sha256(List.of(lessonId)),
                            claim(claims, "lesson_scope_sha256"));
                })
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"conversation_id\":\"" + conversationId + "\"}"));

        assertEquals(201, proxy.sendMessage(userId, "STUDENT", courseId, requestId,
                conversationId, key, "Question").getStatusCode().value());
        server.verify();
    }

    @Test
    void deniedCourseNeverContactsPrivateService() {
        when(policy.requireCourseAccess(userId, "STUDENT", courseId))
                .thenThrow(new BusinessException(ErrorCode.QUIZ_LOCKED, "Quiz in progress"));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> proxy.status(userId, "STUDENT", courseId, requestId));

        assertEquals(ErrorCode.QUIZ_LOCKED, denied.getCode());
        server.verify();
    }

    @Test
    void deniedLessonScopeNeverSendsAChatTurn() {
        when(policy.requireAuthorizedLessonScope(userId, "STUDENT", courseId))
                .thenThrow(new BusinessException(ErrorCode.QUIZ_LOCKED, "Quiz in progress"));

        BusinessException denied = assertThrows(BusinessException.class,
                () -> proxy.sendMessage(userId, "STUDENT", courseId, requestId,
                        UUID.randomUUID(), UUID.randomUUID(), "Question"));

        assertEquals(ErrorCode.QUIZ_LOCKED, denied.getCode());
        server.verify();
    }

    @Test
    void historyRoutesPreserveMethodsPathsAndPagination() {
        UUID conversationId = UUID.randomUUID();
        String root = "http://m7.internal:8000/internal/v1/ai/courses/" + courseId + "/conversations";
        server.expect(once(), requestTo(root))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"title\":\"New chat\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).body("{}"));
        server.expect(once(), requestTo(root + "?page=2&page_size=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body("{}"));
        server.expect(once(), requestTo(root + "/" + conversationId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body("{}"));
        server.expect(once(), requestTo(root + "/" + conversationId + "/messages?page=3&page_size=5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).body("{}"));
        server.expect(once(), requestTo(root + "/" + conversationId))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        proxy.createConversation(userId, "STUDENT", courseId, requestId, "New chat");
        proxy.listConversations(userId, "STUDENT", courseId, requestId, 2, 10);
        proxy.getConversation(userId, "STUDENT", courseId, requestId, conversationId);
        proxy.listMessages(userId, "STUDENT", courseId, requestId, conversationId, 3, 5);
        assertEquals(204, proxy.deleteConversation(userId, "STUDENT", courseId, requestId,
                conversationId).getStatusCode().value());
        server.verify();
    }

    @Test
    void mapsKnownPrivateErrorAndHidesUnknownFailures() {
        String path = "http://m7.internal:8000/internal/v1/ai/courses/" + courseId + "/status";
        server.expect(once(), requestTo(path))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"AI_INDEX_NOT_READY\",\"message\":\"internal detail\"}}"));
        server.expect(once(), requestTo(path))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("provider-secret=hidden"));
        BusinessException known = assertThrows(BusinessException.class,
                () -> proxy.status(userId, "STUDENT", courseId, requestId));
        assertEquals(ErrorCode.AI_INDEX_NOT_READY, known.getCode());
        assertEquals("AI_INDEX_NOT_READY", known.getMessage());

        BusinessException unknown = assertThrows(BusinessException.class,
                () -> proxy.status(userId, "STUDENT", courseId, requestId));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, unknown.getCode());
        assertEquals("AI Tutor is unavailable", unknown.getMessage());
        server.verify();
    }

    @Test
    void enabledProxyRequiresAnExplicitPrivateServiceUrl() {
        assertThrows(IllegalArgumentException.class, () -> new AiTutorProxyService(
                policy, issuer, RestClient.builder(), new ObjectMapper(), ""));
    }

    private static JWTClaimsSet parseClaims(String authorization) {
        try {
            return SignedJWT.parse(authorization.substring(7)).getJWTClaimsSet();
        } catch (ParseException ex) {
            throw new AssertionError("Malformed service assertion", ex);
        }
    }

    private static String claim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException ex) {
            throw new AssertionError("Malformed service assertion claim", ex);
        }
    }
}
