package com.oep.corelms.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Issues a dedicated, course-bound assertion after the caller has passed M7 access checks. */
@Component
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorAssertionIssuer {

    private static final long TTL_SECONDS = 60;
    private final byte[] signingKey;

    public AiTutorAssertionIssuer(@Value("${oep.ai-tutor.service-jwt-secret:}") String secret) {
        signingKey = secret.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 32) {
            throw new IllegalArgumentException("AI_TUTOR_SERVICE_JWT_SECRET must contain at least 32 bytes");
        }
    }

    public String issueStudent(UUID userId, UUID courseId, UUID requestId) {
        return issueStudentAt(userId, courseId, requestId, null, Instant.now());
    }

    String issueStudentAt(UUID userId, UUID courseId, UUID requestId, Instant now) {
        return issueStudentAt(userId, courseId, requestId, null, now);
    }

    /** The caller must obtain lessonIds from AiTutorAccessPolicy in this request. */
    public String issueStudentWithLessonScope(UUID userId, UUID courseId, UUID requestId,
                                              List<UUID> lessonIds) {
        return issueStudentAt(userId, courseId, requestId, AiTutorLessonScope.sha256(lessonIds), Instant.now());
    }

    /** For the private indexing API only; no end-user identity or course access is implied. */
    public String issueIndexer(UUID requestId) {
        return issueIndexerAt(requestId, Instant.now());
    }

    String issueIndexerAt(UUID requestId, Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("oep-core-lms")
                .audience("m7-ai-tutor")
                .subject("oep-core-lms")
                .claim("request_id", requestId.toString())
                .claim("scope", "ai:index")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TTL_SECONDS)))
                .build();
        return sign(claims);
    }

    private String issueStudentAt(UUID userId, UUID courseId, UUID requestId, String lessonScopeSha256,
                                  Instant now) {
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer("oep-core-lms")
                .audience("m7-ai-tutor")
                .subject(userId.toString())
                .claim("course_id", courseId.toString())
                .claim("request_id", requestId.toString())
                .claim("scope", "ai:student")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TTL_SECONDS)));
        if (lessonScopeSha256 != null) {
            claimsBuilder.claim("lesson_scope_sha256", lessonScopeSha256);
        }
        JWTClaimsSet claims = claimsBuilder.build();
        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                claims
        );
        try {
            token.sign(new MACSigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign AI Tutor assertion", e);
        }
        return token.serialize();
    }
}
