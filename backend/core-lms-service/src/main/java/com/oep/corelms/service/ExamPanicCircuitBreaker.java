package com.oep.corelms.service;

import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuditAction;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamPanicCircuitBreaker {
    public static final String SETTING_KEY = "EXAM_PANIC_CIRCUIT_BREAKER";
    private static final UUID AUDIT_TARGET = UUID.nameUUIDFromBytes(SETTING_KEY.getBytes(StandardCharsets.UTF_8));

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;

    public record State(boolean enabled, String reason, String updatedBy, Instant updatedAt) {}

    public boolean isEnabled() {
        Boolean enabled = jdbc.queryForObject("""
                select coalesce((select boolean_value from system_settings where setting_key = ?), false)
                """, Boolean.class, SETTING_KEY);
        return Boolean.TRUE.equals(enabled);
    }

    public void requireAncillaryServicesAvailable() {
        if (isEnabled()) {
            throw new BusinessException(ErrorCode.ANCILLARY_SERVICES_PAUSED,
                    "Các dịch vụ AI và phân tích phụ trợ đang tạm dừng để bảo vệ kỳ thi");
        }
    }

    @Transactional(readOnly = true)
    public State state(UUID actor) {
        requireAdmin(actor);
        return jdbc.queryForObject("""
                select s.boolean_value, s.reason, u.email, s.updated_at
                from system_settings s left join users u on u.id = s.updated_by
                where s.setting_key = ?
                """, (rs, row) -> new State(rs.getBoolean("boolean_value"), rs.getString("reason"),
                rs.getString("email"), rs.getTimestamp("updated_at").toInstant()), SETTING_KEY);
    }

    @Transactional
    public State set(UUID actor, boolean enabled, String reason) {
        requireAdmin(actor);
        boolean previous = isEnabled();
        jdbc.update("""
                insert into system_settings (setting_key, boolean_value, reason, updated_by, updated_at)
                values (?, ?, ?, ?, now())
                on conflict (setting_key) do update set boolean_value = excluded.boolean_value,
                    reason = excluded.reason, updated_by = excluded.updated_by, updated_at = now()
                """, SETTING_KEY, enabled, reason.strip(), actor);
        audit.record(actor, AuditAction.UPDATE_SYSTEM_SETTING, "SYSTEM_SETTING", AUDIT_TARGET,
                Map.of("key", SETTING_KEY, "enabled", enabled, "previousEnabled", previous, "reason", reason.strip()));
        return state(actor);
    }

    private void requireAdmin(UUID actor) {
        boolean allowed = actor != null && users.findById(actor)
                .map(user -> user.getRole() == UserRole.ADMIN && user.getAccountStatus() == AccountStatus.ACTIVE)
                .orElse(false);
        if (!allowed) throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Chỉ Admin đang hoạt động được thay đổi cài đặt thi");
    }
}
