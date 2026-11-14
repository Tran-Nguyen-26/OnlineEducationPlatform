package com.oep.corelms.service;

import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "oep.badges.signing-key-file")
public class CourseCredentialService {
    private final EnrollmentRepository enrollments;
    private final GraduationService graduation;
    private final BadgeSigner signer;
    private final JdbcTemplate jdbc;
    private final NotificationPublisher notifications;

    public record Credential(UUID id, String verificationUrl, String signedCredential) {}
    public record Verification(UUID id, String courseTitle, String issuedAt, String issuer) {}

    @Transactional
    public Credential issue(UUID actor, UUID course) {
        var enrollment = enrollments.lockForAssessment(course, actor)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Cần ghi danh để nhận chứng chỉ"));
        var existing = jdbc.queryForList("select id from course_credentials where enrollment_id = ?", UUID.class, enrollment.getId());
        if (!existing.isEmpty()) return credential(existing.getFirst());
        if (!graduation.get(actor, course).eligible()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chưa đủ điều kiện cấp chứng chỉ");
        }
        UUID id = UUID.randomUUID();
        String signed = signer.sign(id, course, enrollment.getCourse().getTitle(), Instant.now());
        jdbc.update("insert into course_credentials(id, enrollment_id, signed_credential) values (?, ?, ?)",
                id, enrollment.getId(), signed);
        notifications.publishNotificationEvent("COURSE_CERTIFICATE_ISSUED", "COURSE_CREDENTIAL", id, actor,
                "Chứng chỉ khóa học đã sẵn sàng", "Bạn đã hoàn thành khóa học “" + enrollment.getCourse().getTitle() + "”.",
                "/learner/courses/" + course + "/certificate", Map.of("courseId", course));
        return new Credential(id, signer.verificationUrl(id), signed);
    }

    @Transactional(readOnly = true)
    public Credential owned(UUID actor, UUID course) {
        var enrollment = enrollments.findByCourseIdAndStudentId(course, actor).orElseThrow(this::missing);
        return jdbc.queryForList("select id from course_credentials where enrollment_id = ?", UUID.class, enrollment.getId())
                .stream().findFirst().map(this::credential).orElseThrow(this::missing);
    }

    @Transactional(readOnly = true)
    public Verification verify(UUID id) {
        var claims = signer.verify(id, credential(id).signedCredential()).orElseThrow(this::missing);
        return new Verification(id, (String) claims.get("name"), (String) claims.get("validFrom"),
                (String) ((Map<?, ?>) claims.get("issuer")).get("name"));
    }

    private Credential credential(UUID id) {
        return jdbc.queryForList("select signed_credential from course_credentials where id = ?", String.class, id)
                .stream().findFirst().map(value -> new Credential(id, signer.verificationUrl(id), value)).orElseThrow(this::missing);
    }

    private BusinessException missing() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chứng chỉ hợp lệ");
    }
}
