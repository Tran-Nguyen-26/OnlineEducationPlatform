package com.oep.corelms.service;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeSignerTest {
    @Test
    void verifiesIssuedBadgeAndRejectsTamperedPayloadAndOtherIds() throws Exception {
        var signer = new BadgeSigner(new RSAKeyGenerator(2048).generate(), "https://example.test", "Issuer");
        UUID id = UUID.randomUUID();
        String credential = signer.sign(id, UUID.randomUUID(), "Course", Instant.now());
        assertThat(signer.verify(id, credential).orElseThrow()).containsEntry("name", "Course");
        assertThat(signer.publicKey()).doesNotContainKeys("d", "p", "q");
        assertThat(signer.verify(UUID.randomUUID(), credential)).isEmpty();
        var parts = credential.split("\\.");
        var claims = JWSObject.parse(credential).getPayload().toJSONObject();
        claims.put("name", "Modified");
        String tampered = parts[0] + "." + new Payload(claims).toBase64URL() + "." + parts[2];
        assertThat(signer.verify(id, tampered)).isEmpty();
    }
}
