package com.oep.corelms.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Core LMS is the JWT issuer. The RSA signing key is NOT regenerated on every start any more:
 * <ol>
 *   <li>{@code oep.jwt.private-key-file} (PKCS#8 PEM) — production: mounted from a secret store; startup fails if it is missing under profile "prod".</li>
 *   <li>otherwise (dev) a key is generated once and persisted under {@code oep.jwt.key-dir}, so restarts keep every issued token valid.</li>
 * </ol>
 * The key id is the RFC 7638 thumbprint of the public key. For rotation, keep verifying with the previous public keys
 * listed in {@code oep.jwt.previous-public-key-files} (they stay in the JWKS), then drop them once their tokens expired.
 */
@Slf4j
@Component
public class JwtKeyService {

    @Getter
    private final RSAKey rsaKey;
    private final List<RSAKey> previousKeys = new ArrayList<>();

    public JwtKeyService(Environment env,
                         @Value("${oep.jwt.private-key-file:}") String privateKeyFile,
                         @Value("${oep.jwt.private-key-pem:}") String privateKeyPem,
                         @Value("${oep.jwt.key-dir:${user.home}/.oep/jwt}") String keyDir,
                         @Value("${oep.jwt.previous-public-key-files:}") String previousFiles) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (privateKeyFile.isBlank() && !privateKeyPem.isBlank()) {
            // Platforms that inject secrets as environment variables (ECS + Secrets Manager) provide the PEM inline.
            try {
                Path tmp = Files.createTempFile("oep-jwt-", ".pem");
                try { Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------")); } catch (Exception ignored) { /* non-POSIX fs */ }
                Files.writeString(tmp, privateKeyPem.replace("\\n", "\n"), StandardCharsets.US_ASCII);
                tmp.toFile().deleteOnExit();
                privateKeyFile = tmp.toString();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot materialise oep.jwt.private-key-pem", e);
            }
        }
        try {
            KeyPair pair;
            if (!privateKeyFile.isBlank()) {
                pair = fromPrivateKeyFile(Path.of(privateKeyFile));
                log.info("JWT signing key loaded from {}", privateKeyFile);
            } else if (prod) {
                throw new IllegalStateException("oep.jwt.private-key-file is required under profile 'prod' (refusing to generate an ephemeral signing key)");
            } else {
                pair = loadOrCreateDevKey(Path.of(keyDir));
            }
            RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
            this.rsaKey = new RSAKey.Builder(pub).privateKey(pair.getPrivate()).keyUse(KeyUse.SIGNATURE)
                    .keyIDFromThumbprint().build();
            for (String f : previousFiles.split(",")) {
                if (!f.isBlank()) {
                    RSAPublicKey p = readPublicPem(Path.of(f.trim()));
                    previousKeys.add(new RSAKey.Builder(p).keyUse(KeyUse.SIGNATURE).keyIDFromThumbprint().build());
                }
            }
            log.info("JWT signer ready: kid={} (+{} previous verification key(s))", rsaKey.getKeyID(), previousKeys.size());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise JWT keys: " + e.getMessage(), e);
        }
    }

    /** Public keys published in the JWKS: the current one first, then those kept for rotation. */
    public JWKSet publicJwkSet() {
        List<com.nimbusds.jose.jwk.JWK> keys = new ArrayList<>();
        keys.add(rsaKey.toPublicJWK());
        previousKeys.forEach(k -> keys.add(k.toPublicJWK()));
        return new JWKSet(keys);
    }

    public RSAKey publicJwk() {
        return rsaKey.toPublicJWK();
    }

    private static KeyPair loadOrCreateDevKey(Path dir) throws Exception {
        Path priv = dir.resolve("private.pem");
        if (Files.exists(priv)) {
            log.info("JWT dev key loaded from {}", priv);
            return fromPrivateKeyFile(priv);
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        Files.createDirectories(dir);
        write(priv, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), true);
        write(dir.resolve("public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()), false);
        log.warn("Generated a NEW dev JWT key at {} (set oep.jwt.private-key-file for real deployments)", dir);
        return pair;
    }

    private static void write(Path p, String content, boolean secret) throws IOException {
        Files.writeString(p, content, StandardCharsets.US_ASCII);
        if (secret) {
            try { Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------")); } catch (Exception ignored) { /* non-POSIX fs */ }
        }
    }

    private static KeyPair fromPrivateKeyFile(Path file) throws Exception {
        byte[] der = Base64.getMimeDecoder().decode(Files.readString(file).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        return new KeyPair(kf.generatePublic(new RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent())), priv);
    }

    private static RSAPublicKey readPublicPem(Path file) throws Exception {
        byte[] der = Base64.getMimeDecoder().decode(Files.readString(file).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    @SuppressWarnings("unused")
    private static void touch(JOSEException e) { /* keeps the Nimbus checked exception import meaningful for builders */ }
}
