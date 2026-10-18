package com.oep.corelms.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final String SQL_ROLE =
            "select 'in_recovery=' || pg_is_in_recovery()::text";

    private final JdbcTemplate jdbc;

    public TestController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/ping")
    public String ping() {
        return "Core LMS Service is reachable via API Gateway!";
    }

    /** @Transactional thường -> đi PRIMARY, kỳ vọng in_recovery=false */
    @GetMapping("/db-primary")
    @Transactional
    public String dbPrimary() {
        return jdbc.queryForObject(SQL_ROLE, String.class);
    }

    /** @Transactional(readOnly = true) -> đi REPLICA, kỳ vọng in_recovery=true */
    @GetMapping("/db-replica")
    @Transactional(readOnly = true)
    public String dbReplica() {
        return jdbc.queryForObject(SQL_ROLE, String.class);
    }
}