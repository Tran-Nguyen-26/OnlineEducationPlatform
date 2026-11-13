package com.oep.corelms.config;

import com.nimbusds.jose.jwk.RSAKey;
import com.oep.corelms.service.BadgeSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "oep.badges.signing-key-file")
public class BadgeConfiguration {
    @Bean
    BadgeSigner badgeSigner(@Value("${oep.badges.signing-key-file}") Path path,
                            @Value("${oep.badges.public-origin}") URI origin,
                            @Value("${oep.badges.issuer-name:OEP}") String issuer) throws Exception {
        if (origin.getHost() == null || !("https".equals(origin.getScheme())
                || ("http".equals(origin.getScheme()) && ("localhost".equals(origin.getHost()) || "127.0.0.1".equals(origin.getHost()))))
                || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || "/".equals(origin.getPath()))) {
            throw new IllegalArgumentException("Badge public origin must be HTTPS or local HTTP");
        }
        return new BadgeSigner(RSAKey.parse(Files.readString(path)), origin.toString(), issuer);
    }
}
