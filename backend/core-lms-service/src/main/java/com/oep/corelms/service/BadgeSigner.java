package com.oep.corelms.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class BadgeSigner {
    private final RSAKey key;
    private final String origin;
    private final String issuerName;

    public BadgeSigner(RSAKey key, String origin, String issuerName) {
        if (!key.isPrivate() || key.size() < 2048) throw new IllegalArgumentException("A private RSA badge key of at least 2048 bits is required");
        this.key = key;
        this.origin = origin.replaceFirst("/$", "");
        this.issuerName = issuerName;
    }

    public String sign(UUID id, UUID course, String title, Instant time) {
        Instant issued = time.truncatedTo(ChronoUnit.SECONDS);
        String credentialId = verificationUrl(id);
        String subject = "urn:uuid:" + UUID.randomUUID();
        var claims = Map.ofEntries(
                Map.entry("@context", List.of("https://www.w3.org/ns/credentials/v2", "https://purl.imsglobal.org/spec/ob/v3p0/context-3.0.3.json")),
                Map.entry("type", List.of("VerifiableCredential", "OpenBadgeCredential")),
                Map.entry("id", credentialId), Map.entry("jti", credentialId),
                Map.entry("iss", issuerId()), Map.entry("issuer", issuer()), Map.entry("sub", subject),
                Map.entry("nbf", issued.getEpochSecond()), Map.entry("validFrom", issued.toString()),
                Map.entry("name", title),
                Map.entry("credentialSubject", Map.of("id", subject, "type", List.of("AchievementSubject"),
                        "achievement", Map.of("id", origin + "/courses/" + course, "type", List.of("Achievement"),
                                "name", title, "description", "Hoàn thành khóa học " + title,
                                "criteria", Map.of("narrative", "Hoàn thành các bài bắt buộc, đạt ngưỡng tốt nghiệp và hoàn tất khảo sát.")))));
        try {
            var signed = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT).keyID(keyId()).build(), new Payload(claims));
            signed.sign(new RSASSASigner(key));
            return signed.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Could not sign badge", ex);
        }
    }

    public Optional<Map<String, Object>> verify(UUID id, String credential) {
        try {
            JWSObject signed = JWSObject.parse(credential);
            if (!JWSAlgorithm.RS256.equals(signed.getHeader().getAlgorithm()) || !keyId().equals(signed.getHeader().getKeyID())
                    || !signed.verify(new RSASSAVerifier(key.toPublicJWK()))) return Optional.empty();
            Map<String, Object> claims = signed.getPayload().toJSONObject();
            if (claims == null || !verificationUrl(id).equals(claims.get("id")) || !issuerId().equals(claims.get("iss"))
                    || !claims.get("id").equals(claims.get("jti")) || !(claims.get("nbf") instanceof Number nbf)
                    || nbf.longValue() > Instant.now().getEpochSecond()
                    || !Instant.ofEpochSecond(nbf.longValue()).toString().equals(claims.get("validFrom"))
                    || !(claims.get("credentialSubject") instanceof Map<?, ?> subject) || !Objects.equals(subject.get("id"), claims.get("sub")))
                return Optional.empty();
            return Optional.of(claims);
        } catch (ParseException | JOSEException | java.time.DateTimeException ex) {
            return Optional.empty();
        }
    }

    public String verificationUrl(UUID id) { return origin + "/verify/" + id; }
    private String issuerId() { return origin + "/api/public/credentials/issuer"; }
    private String keyId() { return origin + "/api/public/credentials/key"; }
    public Map<String, Object> issuer() {
        return Map.of("id", issuerId(), "type", List.of("Profile"), "name", issuerName, "url", origin);
    }
    public Map<String, Object> publicKey() {
        var result = new HashMap<>(key.toPublicJWK().toJSONObject());
        result.put("kid", keyId());
        return Map.copyOf(result);
    }
}
