package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiUsageRecorder {
    private final JdbcTemplate jdbc;

    @Transactional
    public void record(UUID courseId, String operation, int promptTokens, int completionTokens,
                       int latencyMs, boolean refused) {
        jdbc.update("""
                insert into ai_usage_events
                  (id, course_id, operation, prompt_tokens, completion_tokens, latency_ms, is_refused, created_at)
                values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now())
                """, courseId, operation, Math.max(0, promptTokens), Math.max(0, completionTokens),
                Math.max(0, latencyMs), refused);
    }
}
