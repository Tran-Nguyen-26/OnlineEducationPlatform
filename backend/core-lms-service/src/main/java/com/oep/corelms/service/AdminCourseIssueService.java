package com.oep.corelms.service;

import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.enums.AuditAction;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminCourseIssueService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationPublisher notifications;

    public enum Decision { RESOLVED, REJECTED }
    public record Issue(UUID id, UUID courseId, String courseTitle, String instructorEmail,
                        String category, String detail, String status, String resolution,
                        Instant createdAt, Instant updatedAt) {}

    @Transactional(readOnly = true)
    public List<Issue> list(UUID actor, String status) {
        requireAdmin(actor);
        String normalized = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.toUpperCase();
        if (normalized != null && !List.of("OPEN", "RESOLVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Trạng thái cờ báo lỗi không hợp lệ");
        }
        return jdbc.query("""
                select f.id, f.course_id, c.title, u.email, f.category, f.detail, f.status, f.resolution,
                       f.created_at, f.updated_at
                from course_issue_flags f join courses c on c.id = f.course_id
                join users u on u.id = f.instructor_id
                where (? is null or f.status = ?)
                order by case when f.status = 'OPEN' then 0 else 1 end, f.created_at desc
                limit 500
                """, (rs, row) -> new Issue(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getString("title"), rs.getString("email"), rs.getString("category"), rs.getString("detail"),
                rs.getString("status"), rs.getString("resolution"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), normalized, normalized);
    }

    @Transactional
    public Issue decide(UUID actor, UUID issueId, Decision decision, String resolution) {
        requireAdmin(actor);
        UUID courseId;
        UUID instructorId;
        try {
            Object[] row = jdbc.queryForObject("""
                    update course_issue_flags set status = ?, resolution = ?, resolved_by = ?, updated_at = now()
                    where id = ? and status = 'OPEN'
                    returning course_id, instructor_id
                    """, (rs, index) -> new Object[]{rs.getObject("course_id", UUID.class), rs.getObject("instructor_id", UUID.class)},
                    decision.name(), resolution.strip(), actor, issueId);
            courseId = (UUID) row[0];
            instructorId = (UUID) row[1];
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Cờ báo lỗi không tồn tại hoặc đã được xử lý");
        }
        audit.record(actor, AuditAction.RESOLVE_COURSE_ISSUE, "COURSE_ISSUE", issueId,
                Map.of("courseId", courseId, "decision", decision.name(), "resolution", resolution.strip()));
        notifications.publishNotificationEvent("COURSE_ISSUE_REVIEWED", "COURSE_ISSUE", issueId,
                instructorId, decision == Decision.RESOLVED ? "Đã xử lý báo lỗi khóa học" : "Báo lỗi khóa học đã được xem xét",
                resolution.strip(), null, Map.of("courseId", courseId, "decision", decision.name()));
        return list(actor, null).stream().filter(issue -> issue.id().equals(issueId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy cờ báo lỗi sau khi cập nhật"));
    }

    private void requireAdmin(UUID actor) {
        if (actor == null || users.findById(actor).filter(user -> user.getRole() == UserRole.ADMIN).isEmpty()) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Chỉ Admin được quản lý cờ báo lỗi");
        }
    }
}
