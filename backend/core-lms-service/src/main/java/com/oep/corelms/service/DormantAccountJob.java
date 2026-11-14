package com.oep.corelms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DormantAccountJob {
    private final JdbcTemplate jdbc;

    @Scheduled(cron = "${oep.iam.dormant-cron:0 0 3 * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void markDormantLearners() {
        int revoked = jdbc.update("""
                with dormant_students as (
                    update users
                    set account_status = 'DORMANT'
                    where role = 'STUDENT' and account_status = 'ACTIVE'
                      and coalesce(last_login_at, created_at) < now() - interval '24 months'
                    returning id
                )
                update refresh_tokens r set is_revoked = true
                from dormant_students d
                where r.user_id = d.id and r.is_revoked = false
                """);
        if (revoked > 0) log.info("Revoked {} refresh sessions for dormant learners", revoked);
    }
}
