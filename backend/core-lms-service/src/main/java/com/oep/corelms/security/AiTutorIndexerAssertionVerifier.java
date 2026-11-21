package com.oep.corelms.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Verify the internal indexer token before any M2 source metadata is read. */
@Component
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorIndexerAssertionVerifier {
    private final byte[] secret;

    public AiTutorIndexerAssertionVerifier(@Value("${oep.ai-tutor.service-jwt-secret:}") String value) {
        secret = value.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalArgumentException("AI_TUTOR_SERVICE_JWT_SECRET must contain at least 32 bytes");
        }
    }

    public void verify(String authorization, String requestId) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            String token = authorization.substring(7);
            if (token.isBlank() || !token.equals(token.trim())) throw new IllegalArgumentException();
            UUID expectedRequest = UUID.fromString(requestId);
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) throw new IllegalArgumentException();
            var claims = jwt.getJWTClaimsSet();
            Date issued = claims.getIssueTime();
            Date notBefore = claims.getNotBeforeTime();
            Date expires = claims.getExpirationTime();
            if (!"m7-ai-tutor".equals(claims.getIssuer())
                    || !List.of("oep-core-lms").equals(claims.getAudience())
                    || !"m7-ai-tutor".equals(claims.getSubject())
                    || !"ai:source:read".equals(claims.getStringClaim("scope"))
                    || issued == null || notBefore == null || expires == null
                    || expires.getTime() - issued.getTime() <= 0
                    || expires.getTime() - issued.getTime() > 60_000
                    || notBefore.before(issued)
                    || issued.toInstant().isAfter(Instant.now().plusSeconds(3))
                    || notBefore.toInstant().isAfter(Instant.now().plusSeconds(3))
                    || expires.toInstant().isBefore(Instant.now().minusSeconds(3))
                    || !expectedRequest.equals(UUID.fromString(claims.getStringClaim("request_id")))) {
                throw new IllegalArgumentException();
            }
            UUID.fromString(claims.getJWTID());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal index assertion");
        }
    }
}
