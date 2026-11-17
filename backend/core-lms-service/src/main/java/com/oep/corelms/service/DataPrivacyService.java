package com.oep.corelms.service;

import com.oep.corelms.security.TokenRevocationService;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import com.oep.coremodel.entity.identity.User;
import com.oep.corelms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personal-data rights: (1) a user can download everything the platform holds about them, (2) an admin can erase a
 * person's identity while keeping the academic records (enrollments, grades) the school must retain — those rows stay,
 * but point to an anonymised user.
 */
@Service
@RequiredArgsConstructor
public class DataPrivacyService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final TokenRevocationService tokenRevocation;

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("account", one("select id, email, role, account_status, created_at from users where id = ?", userId));
        out.put("profile", one("select full_name, phone, bio, date_of_birth, organization, avatar_object_key from user_profiles where user_id = ?", userId));
        out.put("enrollments", rows("select e.id, e.course_id, c.title as course_title, e.status, e.enrolled_at, e.completed_at "
                + "from enrollments e join courses c on c.id = e.course_id where e.student_id = ? order by e.enrolled_at", userId));
        out.put("lessonProgress", rows("select p.lesson_id, p.status, p.watched_percent, p.time_spent_seconds, p.completed_at "
                + "from lesson_progress p join enrollments e on e.id = p.enrollment_id where e.student_id = ?", userId));
        out.put("quizAttempts", rows("select a.id, a.quiz_id, a.attempt_number, a.status, a.started_at, a.submitted_at "
                + "from quiz_attempts a join enrollments e on e.id = a.enrollment_id where e.student_id = ? order by a.started_at", userId));
        out.put("assignmentSubmissions", rows("select s.id, s.assignment_id, s.status, s.is_late, s.submitted_at, s.text_content, g.score, g.feedback "
                + "from assignment_submissions s join enrollments e on e.id = s.enrollment_id "
                + "left join assignment_grades g on g.submission_id = s.id where e.student_id = ?", userId));
        out.put("certificates", rows("select c.certificate_number, c.issued_at from certificates c join enrollments e on e.id = c.enrollment_id where e.student_id = ?", userId));
        out.put("notifications", rows("select event_type, title, message, is_read, created_at from notifications where user_id = ? order by created_at desc limit 1000", userId));
        out.put("notificationPreferences", rows("select event_type, in_app_enabled, email_enabled, reminder_before_minutes from notification_preferences where user_id = ?", userId));
        return out;
    }

    /** Irreversible. Returns the anonymised e-mail placeholder. */
    @Transactional
    public String anonymize(UUID targetId, UUID actingAdminId) {
        if (targetId.equals(actingAdminId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "Không thể ẩn danh chính tài khoản đang đăng nhập");
        }
        User user = users.findById(targetId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không thể ẩn danh tài khoản quản trị");
        }
        String placeholder = "deleted-" + targetId + "@anonymized.invalid";
        user.setEmail(placeholder);
        user.setAccountStatus(AccountStatus.DISABLED);
        users.save(user);
        jdbc.update("update user_profiles set full_name = 'Người dùng đã xóa', phone = null, bio = null, date_of_birth = null, "
                + "organization = null, avatar_object_key = null where user_id = ?", targetId);
        jdbc.update("delete from auth_identities where user_id = ?", targetId);   // no credential can ever match again
        jdbc.update("delete from refresh_tokens where user_id = ?", targetId);
        jdbc.update("delete from notifications where user_id = ?", targetId);
        jdbc.update("delete from notification_preferences where user_id = ?", targetId);
        tokenRevocation.revokeAllAccessTokens(targetId);
        return placeholder;
    }

    private Map<String, Object> one(String sql, UUID id) {
        List<Map<String, Object>> r = jdbc.queryForList(sql, id);
        return r.isEmpty() ? Map.of() : r.get(0);
    }

    private List<Map<String, Object>> rows(String sql, UUID id) {
        return jdbc.queryForList(sql, id);
    }
}
