package com.oep.corelms.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiTutorIndexerAssertionVerifierTest {
    private static final String SECRET = "dedicated-index-secret-longer-than-32-bytes";
    private final AiTutorIndexerAssertionVerifier verifier = new AiTutorIndexerAssertionVerifier(SECRET);

    @Test
    void acceptsFreshIndexerOnlyForBoundRequest() throws Exception {
        UUID requestId = UUID.randomUUID();
        verifier.verify("Bearer " + token(requestId, Instant.now(), "ai:source:read"),
                requestId.toString());
    }

    @Test
    void rejectsStudentWrongRequestTamperingAndExpiry() throws Exception {
        UUID requestId = UUID.randomUUID();
        String token = token(requestId, Instant.now(), "ai:source:read");
        assertUnauthorized(() -> verifier.verify("Bearer " + token, UUID.randomUUID().toString()));
        assertUnauthorized(() -> verifier.verify("Bearer " + token + "x", requestId.toString()));
        assertUnauthorized(() -> verifier.verify("Bearer " + new AiTutorAssertionIssuer(SECRET).issueStudent(
                UUID.randomUUID(), UUID.randomUUID(), requestId), requestId.toString()));
        assertUnauthorized(() -> verifier.verify(null, requestId.toString()));
        assertUnauthorized(() -> verifier.verify("Bearer " + token(requestId,
                Instant.now().minusSeconds(120), "ai:source:read"), requestId.toString()));
        assertUnauthorized(() -> verifier.verify("Bearer " + token(requestId,
                Instant.now(), "ai:index"), requestId.toString()));
    }

    private static String token(UUID requestId, Instant now, String scope) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("m7-ai-tutor").audience("oep-core-lms")
                    .subject("m7-ai-tutor").claim("scope", scope)
                    .claim("request_id", requestId.toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(60))).build();
            SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertUnauthorized(Runnable action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }
}
