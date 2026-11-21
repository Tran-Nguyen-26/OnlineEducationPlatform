package com.oep.corelms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oep.corelms.security.AiTutorIndexerAssertionVerifier;
import com.oep.corelms.service.AiTutorSourceReadService;
import com.oep.coremodel.enums.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiTutorSourceControllerTest {
    private static final String SECRET = "source-controller-secret-longer-than-32-bytes";

    @Test
    void rejectsUnsignedReadBeforeTouchingSource() throws Exception {
        var sources = mock(AiTutorSourceReadService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AiTutorSourceController(
                new AiTutorIndexerAssertionVerifier(SECRET), sources)).build();
        UUID content = UUID.randomUUID();
        mvc.perform(get("/internal/v1/ai/sources/{contentId}", content)
                        .param("course_id", UUID.randomUUID().toString())
                        .param("lesson_id", UUID.randomUUID().toString())
                        .param("version_number", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(sources);
    }

    @Test
    void bindsSignedReadToExactSourceScopeAndVersion() throws Exception {
        var sources = mock(AiTutorSourceReadService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AiTutorSourceController(
                new AiTutorIndexerAssertionVerifier(SECRET), sources)).build();
        UUID content = UUID.randomUUID(), course = UUID.randomUUID(), lesson = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(sources.read(content, course, lesson, 7)).thenReturn(new AiTutorSourceReadService.Source(
                content, course, lesson, 7, ContentType.TEXT, "Lesson", "Grounding",
                null, null, null, null));
        mvc.perform(get("/internal/v1/ai/sources/{contentId}", content)
                        .param("course_id", course.toString())
                        .param("lesson_id", lesson.toString())
                        .param("version_number", "7")
                        .header("X-Request-Id", requestId.toString())
                        .header("Authorization", "Bearer " + token(requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content_id").value(content.toString()))
                .andExpect(jsonPath("$.version_number").value(7))
                .andExpect(jsonPath("$.text_content").value("Grounding"))
                .andExpect(result -> {
                    var body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
                    for (String nullable : new String[]{"file_name", "mime_type", "file_size", "download_url"}) {
                        assertTrue(body.has(nullable), nullable + " must be present as null");
                    }
                });
        verify(sources).read(content, course, lesson, 7);
    }

    private static String token(UUID requestId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("m7-ai-tutor").audience("oep-core-lms")
                .subject("m7-ai-tutor").claim("scope", "ai:source:read")
                .claim("request_id", requestId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60))).build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return signed.serialize();
    }
}
