package com.oep.corelms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/** Under profile "prod", refuse to start with any of the well-known development secrets still in place. */
@Component
@Profile("prod")
public class ProdSecretsGuard {

    @Value("${oep.admin.seed.password:}") private String adminPassword;
    @Value("${oep.datasource.primary.password:}") private String dbPassword;
    @Value("${spring.rabbitmq.password:}") private String rabbitPassword;
    @Value("${oep.storage.secret-key:}") private String storageSecret;

    @PostConstruct
    void check() {
        List<String> bad = new ArrayList<>();
        if (weak(adminPassword, "Admin@12345")) bad.add("ADMIN_SEED_PASSWORD");
        if (weak(dbPassword, "oep")) bad.add("DB_PASSWORD");
        if (weak(rabbitPassword, "oep")) bad.add("RABBIT_PASSWORD");
        if (weak(storageSecret, "oep_secret")) bad.add("STORAGE_SECRET_KEY");
        if (!bad.isEmpty()) {
            throw new IllegalStateException("Refusing to start in 'prod' with default/empty secrets: " + String.join(", ", bad));
        }
    }

    private static boolean weak(String value, String devDefault) {
        return value == null || value.isBlank() || value.equals(devDefault) || value.length() < 8;
    }
}
