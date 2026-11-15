package com.oep.corelms.security;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Core LMS is the JWT issuer: holds the RSA signing key for the lifetime of the process.
 * Dev-only simplification (see CLAUDE.md): the keypair is generated fresh on every restart,
 * so tokens issued before a restart stop validating. Fine for local/dev; a real deployment
 * would load a persisted key from a secret store instead.
 */
@Getter
@Component
public class JwtKeyService {

    private final RSAKey rsaKey;

    public JwtKeyService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    public RSAKey publicJwk() {
        return rsaKey.toPublicJWK();
    }
}
