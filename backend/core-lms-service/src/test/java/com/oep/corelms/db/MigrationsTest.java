package com.oep.corelms.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A brand-new Postgres must come up through the Flyway migrations alone (what every fresh environment does). */
@Testcontainers(disabledWithoutDocker = true)
class MigrationsTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.14");

    @Test
    void freshDatabaseMigratesToLatestAndHasExpectedSchema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        var result = flyway.migrate();
        assertTrue(result.success);
        assertEquals("6", flyway.info().current().getVersion().getVersion(), "latest migration is V6");

        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            assertEquals(1, scalar(s, "select count(*) from information_schema.tables where table_name = 'shedlock'"));
            assertEquals(2, scalar(s, "select count(*) from information_schema.columns where table_name = 'lesson_contents' and column_name in ('transcode_status','hls_prefix')"));
            assertEquals(2, scalar(s, "select count(*) from information_schema.columns where table_name = 'outbox_events' and column_name in ('next_attempt_at','last_error')"));
            assertEquals(1, scalar(s, "select count(*) from information_schema.columns where table_name = 'assignments' and column_name = 'blind_grading'"));
            assertEquals(1, scalar(s, "select count(*) from information_schema.columns where table_name = 'assignment_submissions' and column_name = 'version'"));
            assertEquals(1, scalar(s, "select count(*) from information_schema.columns where table_name = 'users' and column_name = 'consent_at'"));
            assertEquals(2, scalar(s, "select count(*) from information_schema.columns where table_name = 'course_invite_codes' and column_name in ('max_uses','used_count')"));
            assertTrue(scalar(s, "select count(*) from information_schema.tables where table_schema = 'public'") >= 48);
        }
    }

    private static long scalar(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
