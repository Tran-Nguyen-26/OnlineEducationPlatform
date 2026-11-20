package com.oep.corelms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.service.AiTutorProxyService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiTutorControllerTest {

    private final AiTutorProxyService proxy = mock(AiTutorProxyService.class);
    private final AiTutorController controller = new AiTutorController(proxy);
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Jwt student = Jwt.withTokenValue("public-jwt")
            .header("alg", "RS256").subject(userId.toString()).claim("role", "STUDENT").build();

    @Test
    void sendDerivesIdentityAndRejectsClientSuppliedScope() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        when(proxy.sendMessage(eq(userId), eq("STUDENT"), eq(courseId), eq(requestId),
                eq(conversationId), eq(key), eq("Question")))
                .thenReturn(ResponseEntity.status(201).body(new byte[0]));

        assertEquals(201, controller.send(student, courseId, conversationId, requestId.toString(),
                key.toString(), mapper.readTree("{\"message\":\"Question\"}")).getStatusCode().value());
        verify(proxy).sendMessage(userId, "STUDENT", courseId, requestId, conversationId, key, "Question");

        BusinessException invalid = assertThrows(BusinessException.class, () -> controller.send(
                student, courseId, conversationId, requestId.toString(), key.toString(),
                mapper.readTree("{\"message\":\"Question\",\"readable_lesson_ids\":[]}")));
        assertEquals(ErrorCode.VALIDATION_FAILED, invalid.getCode());
    }

    @Test
    void invalidKeysAndPaginationAreRejectedBeforeProxy() throws Exception {
        BusinessException badKey = assertThrows(BusinessException.class, () -> controller.send(
                student, courseId, conversationId, null, "not-a-uuid",
                mapper.readTree("{\"message\":\"Question\"}")));
        assertEquals(ErrorCode.VALIDATION_FAILED, badKey.getCode());

        BusinessException badPage = assertThrows(BusinessException.class, () ->
                controller.list(student, courseId, null, 0, 20));
        assertEquals(ErrorCode.VALIDATION_FAILED, badPage.getCode());
        verifyNoInteractions(proxy);
    }
}
