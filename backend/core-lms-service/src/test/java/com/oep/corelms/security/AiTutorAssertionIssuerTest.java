package com.oep.corelms.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiTutorAssertionIssuerTest {

    private static final String SECRET = "test-only-service-secret-with-at-least-32-bytes";

    @Test
    void signsOnlyVerifiedStudentContextForAtMostSixtySeconds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        AiTutorAssertionIssuer issuer = new AiTutorAssertionIssuer(SECRET);

        SignedJWT token = SignedJWT.parse(issuer.issueStudentAt(userId, courseId, requestId, now));
        assertEquals(JWSAlgorithm.HS256, token.getHeader().getAlgorithm());
        assertTrue(token.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8))));
        assertEquals("oep-core-lms", token.getJWTClaimsSet().getIssuer());
        assertEquals("m7-ai-tutor", token.getJWTClaimsSet().getAudience().getFirst());
        assertEquals(userId.toString(), token.getJWTClaimsSet().getSubject());
        assertEquals(courseId.toString(), token.getJWTClaimsSet().getStringClaim("course_id"));
        assertEquals(requestId.toString(), token.getJWTClaimsSet().getStringClaim("request_id"));
        assertEquals("ai:student", token.getJWTClaimsSet().getStringClaim("scope"));
        assertEquals(now, token.getJWTClaimsSet().getIssueTime().toInstant());
        assertEquals(now, token.getJWTClaimsSet().getNotBeforeTime().toInstant());
        assertEquals(now.plusSeconds(60), token.getJWTClaimsSet().getExpirationTime().toInstant());
        UUID.fromString(token.getJWTClaimsSet().getJWTID());

        SignedJWT anotherToken = SignedJWT.parse(issuer.issueStudentAt(userId, courseId, requestId, now));
        assertNotEquals(token.getJWTClaimsSet().getJWTID(), anotherToken.getJWTClaimsSet().getJWTID());
    }

    @Test
    void rejectsMissingOrShortDedicatedSecret() {
        assertThrows(IllegalArgumentException.class, () -> new AiTutorAssertionIssuer(""));
        assertThrows(IllegalArgumentException.class, () -> new AiTutorAssertionIssuer("too-short"));
    }

    @Test
    void remainsDisabledByDefaultAndRequiresASecretWhenEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(IssuerConfiguration.class);
        runner.run(context -> assertTrue(context.getBeansOfType(AiTutorAssertionIssuer.class).isEmpty()));
        runner.withPropertyValues("oep.ai-tutor.enabled=true")
                .run(context -> assertTrue(context.getStartupFailure() != null));
        runner.withPropertyValues(
                        "oep.ai-tutor.enabled=true",
                        "oep.ai-tutor.service-jwt-secret=" + SECRET)
                .run(context -> assertEquals(1, context.getBeansOfType(AiTutorAssertionIssuer.class).size()));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AiTutorAssertionIssuer.class)
    static class IssuerConfiguration {
    }
}
