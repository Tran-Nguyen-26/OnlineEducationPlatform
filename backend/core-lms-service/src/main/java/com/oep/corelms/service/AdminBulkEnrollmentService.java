package com.oep.corelms.service;

import com.oep.corelms.dto.admin.BulkEnrollRowResult;
import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ghi danh hàng loạt (GAPS.md "Nhóm A"): Admin picks one of the course's own invite codes to attach every row to,
 * pastes a list of student emails, and each one is created (if new, with a temporary password like createTeacher)
 * and enrolled ACTIVE — bypassing the self-service invite-code-validity and prerequisite checks deliberately,
 * since this is an explicit Admin action, but still respecting capacity (overflow goes to the waitlist).
 *
 * Each row runs in its own transaction ({@link BulkEnrollRowService}, REQUIRES_NEW) and any failure is caught
 * here and reported as an ERROR row instead of aborting the batch — a typo in row 50 must not roll back the
 * other 49.
 */
@Service
@RequiredArgsConstructor
public class AdminBulkEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(AdminBulkEnrollmentService.class);

    private final CourseRepository courses;
    private final CourseInviteCodeRepository inviteCodes;
    private final BulkEnrollRowService rowService;

    @Transactional(readOnly = true)
    public List<BulkEnrollRowResult> bulkEnroll(UUID courseId, UUID inviteCodeId, List<String> rawEmails) {
        if (!courses.existsById(courseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Course not found");
        }
        if (inviteCodes.findByIdAndCourse_Id(inviteCodeId, courseId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Invite code not found for this course");
        }

        Set<String> seen = new LinkedHashSet<>();
        List<BulkEnrollRowResult> results = new ArrayList<>();
        for (String raw : rawEmails) {
            String email = raw == null ? "" : raw.trim().toLowerCase();
            if (email.isEmpty()) {
                continue;
            }
            if (!seen.add(email)) {
                results.add(new BulkEnrollRowResult(email, "ERROR", "Trùng lặp trong danh sách", null));
                continue;
            }
            try {
                results.add(rowService.processRow(courseId, inviteCodeId, email));
            } catch (Exception e) {
                log.warn("Bulk-enroll row failed for {}: {}", email, e.getMessage());
                results.add(new BulkEnrollRowResult(email, "ERROR", "Không thể xử lý dòng này", null));
            }
        }
        return results;
    }
}
