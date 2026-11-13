package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchivedVideoQuota {
    private static final int LIMIT = 54_000;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;

    public record Status(int remainingSeconds, LocalDate resetsOn) {}

    public Status status(UUID actor) {
        LocalDate month = month();
        int used = jdbc.queryForList("select seconds_used from archived_video_usage where user_id = ? and usage_month = ?",
                Integer.class, actor, month).stream().findFirst().orElse(0);
        return new Status(LIMIT - used, month.plusMonths(1));
    }

    public void requireAvailable(UUID actor) {
        if (status(actor).remainingSeconds() == 0) throw exhausted();
    }

    public void reserveSegment(UUID actor, int seconds) {
        if (seconds <= 0 || seconds > LIMIT) throw exhausted();
        int changed = jdbc.update("""
                insert into archived_video_usage (user_id, usage_month, seconds_used) values (?, ?, ?)
                on conflict (user_id, usage_month) do update
                set seconds_used = archived_video_usage.seconds_used + excluded.seconds_used
                where archived_video_usage.seconds_used + excluded.seconds_used <= ?
                """, actor, month(), seconds, LIMIT);
        if (changed == 0) throw exhausted();
    }

    private static LocalDate month() { return LocalDate.now(ZONE).withDayOfMonth(1); }

    private BusinessException exhausted() {
        return new BusinessException(ErrorCode.ARCHIVED_VIDEO_LIMIT, "Đã hết hạn mức 15 giờ video của tháng");
    }
}
