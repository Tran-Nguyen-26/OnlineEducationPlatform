package com.oep.corelms.service;

import com.oep.corelms.dto.admin.InviteCodeResponse;
import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin generates the codes students enter to unlock a course; the only way into any course. */
@Service
@RequiredArgsConstructor
public class AdminInviteCodeService {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CourseRepository courses;
    private final UserRepository users;
    private final CourseInviteCodeRepository inviteCodes;

    @Transactional
    public InviteCodeResponse generate(UUID courseId, Instant expiresAt, Integer maxUses, UUID adminId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));

        CourseInviteCode inviteCode = new CourseInviteCode();
        inviteCode.setCourse(course);
        inviteCode.setCode(generateUniqueCode());
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setMaxUses(maxUses);
        inviteCode.setCreatedBy(users.getReferenceById(adminId));

        return InviteCodeResponse.from(inviteCodes.saveAndFlush(inviteCode));
    }

    @Transactional(readOnly = true)
    public List<InviteCodeResponse> list(UUID courseId) {
        return inviteCodes.findByCourse_IdOrderByCreatedAtDesc(courseId).stream().map(InviteCodeResponse::from).toList();
    }

    @Transactional
    public InviteCodeResponse deactivate(UUID courseId, UUID codeId) {
        CourseInviteCode inviteCode = inviteCodes.findByIdAndCourse_Id(codeId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Invite code not found"));
        inviteCode.setActive(false);
        return InviteCodeResponse.from(inviteCodes.saveAndFlush(inviteCode));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!inviteCodes.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique invite code after 10 attempts");
    }
}
