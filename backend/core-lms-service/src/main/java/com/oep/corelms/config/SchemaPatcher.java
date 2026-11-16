package com.oep.corelms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate's ddl-auto=update never alters existing CHECK constraints, so widening an enum (e.g. adding the ESSAY question
 * type) would be rejected by the old constraint on databases created earlier. Dropping the stale constraint is idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaPatcher implements ApplicationRunner {

    private static final String[] STALE_CHECKS = {
            "ALTER TABLE questions DROP CONSTRAINT IF EXISTS questions_question_type_check",
    };

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STALE_CHECKS) {
            try {
                jdbc.execute(sql);
            } catch (Exception e) {
                log.warn("Schema patch skipped ({}): {}", sql, e.getMessage());
            }
        }
    }
}
