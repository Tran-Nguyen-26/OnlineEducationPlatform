package com.oep.corelms.service;

import com.oep.corelms.dto.enrollment.EnrollmentResponse;
import com.oep.corelms.dto.enrollment.AccessCodeDtos;
import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.AccessCode;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
// READ_COMMITTED observes the preceding lock holder's committed enrollment.
// All operations use the primary, including authorization and list reads.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class EnrollmentService {
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseInstructorRepository instructors;
    private final AccessCodeRepository accessCodes;
    private final SecureRandom random = new SecureRandom();

    public AccessCodeDtos.Batch issueBatch(AccessCodeDtos.CreateBatch request) {
        Instant now = Instant.now();
        if ((request.accessDays() != 90 && request.accessDays() != 180) || !request.expiresAt().isAfter(now)) {
            throw error(ErrorCode.VALIDATION_FAILED, "Mã cần thời hạn học 90 hoặc 180 ngày và hạn sử dụng trong tương lai");
        }
        Course course = lockCourse(request.courseId());
        UUID batchId = UUID.randomUUID();
        List<AccessCodeDtos.IssuedCode> issued = new ArrayList<>();
        for (int index = 0; index < request.count(); index++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String raw = HexFormat.of().withUpperCase().formatHex(bytes);
            AccessCode code = new AccessCode();
            code.setCodeHash(hashCodeValue(raw));
            code.setBatchId(batchId);
            code.setCourseId(course.getId());
            code.setKind(request.kind());
            code.setAccessDays(request.accessDays());
            code.setExpiresAt(request.expiresAt());
            issued.add(new AccessCodeDtos.IssuedCode(accessCodes.save(code).getId(), raw));
        }
        return new AccessCodeDtos.Batch(batchId, course.getId(), request.kind(), request.accessDays(), request.expiresAt(), issued);
    }

    public List<AccessCodeDtos.CodeStatus> listBatch(UUID batchId) {
        Instant now = Instant.now();
        List<AccessCode> codes = accessCodes.findByBatchIdOrderById(batchId);
        if (codes.isEmpty()) throw error(ErrorCode.NOT_FOUND, "Không tìm thấy lô mã");
        return codes.stream().map(code -> AccessCodeDtos.CodeStatus.from(code, now)).toList();
    }

    public AccessCodeDtos.CodeStatus revokeCode(UUID id) {
        AccessCode code = accessCodes.findOneById(id)
                .orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Không tìm thấy mã"));
        Instant now = Instant.now();
        switch (code.statusAt(now)) {
            case AVAILABLE -> code.setRevokedAt(now);
            case REVOKED -> { }
            default -> throw error(ErrorCode.ENROLLMENT_STATE_CONFLICT, "Chỉ có thể thu hồi mã đang khả dụng");
        }
        return AccessCodeDtos.CodeStatus.from(code, now);
    }

    public EnrollmentResponse redeem(String rawCode, UUID studentId) {
        User student = requireUser(studentId, UserRole.STUDENT);
        AccessCode code = accessCodes.findByCodeHash(hashCodeValue(rawCode.strip().toUpperCase(Locale.ROOT)))
                .orElseThrow(this::invalidCode);
        Course course = lockCourse(code.getCourseId());
        Instant now = Instant.now();
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(course.getId(), studentId).orElse(null);
        if (code.statusAt(now) == AccessCode.Status.USED && studentId.equals(code.getUsedBy()) && enrollment != null) {
            return EnrollmentResponse.from(enrollment);
        }
        if (code.statusAt(now) != AccessCode.Status.AVAILABLE) throw invalidCode();
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED)
            throw error(ErrorCode.COURSE_NOT_OPEN, "Khóa học chưa mở");
        if (enrollment != null && enrollment.getStatus() == EnrollmentStatus.REMOVED)
            throw error(ErrorCode.FORBIDDEN, "Quyền học đã bị thu hồi");

        Instant start = now;
        if (code.getKind() == AccessCode.Kind.EXTENSION) {
            if (enrollment == null || enrollment.getStatus() == EnrollmentStatus.WITHDRAWN)
                throw error(ErrorCode.ENROLLMENT_STATE_CONFLICT, "Mã gia hạn cần một ghi danh hiện có");
            if (enrollment.getAccessExpiresAt() != null && enrollment.getAccessExpiresAt().isAfter(now))
                start = enrollment.getAccessExpiresAt();
        } else {
            if (enrollment != null && enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
                throw error(ErrorCode.ALREADY_ENROLLED, "Khóa học đã được kích hoạt. Hãy dùng mã gia hạn");
            requireAvailableSeat(course);
            if (enrollment == null) {
                enrollment = new Enrollment();
                enrollment.setCourse(course);
                enrollment.setStudent(student);
            }
            enrollment.setStatus(EnrollmentStatus.ACTIVE);
            enrollment.setCreatedVia("ACTIVE_CODE");
            enrollment.setWithdrawnAt(null);
        }
        enrollment.setAccessExpiresAt(start.plus(code.getAccessDays(), ChronoUnit.DAYS));
        code.setUsedBy(studentId);
        code.setUsedAt(now);
        return EnrollmentResponse.from(enrollments.save(enrollment));
    }

    private BusinessException invalidCode() {
        return error(ErrorCode.INVALID_INVITE_CODE, "Mã không hợp lệ hoặc không còn khả dụng");
    }

    private static String hashCodeValue(String rawCode) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public EnrollmentResponse enroll(UUID courseId, UUID studentId) {
        User student = requireUser(studentId, UserRole.STUDENT);
        Course course = lockCourse(courseId);
        Enrollment enrollment = enrollments.findByCourseIdAndStudentId(courseId, studentId).orElse(null);
        if (enrollment != null) {
            if (enrollment.getStatus() == EnrollmentStatus.REMOVED)
                throw error(ErrorCode.FORBIDDEN, "Removed students cannot enroll again");
            if (enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
                return EnrollmentResponse.from(enrollment);
        }
        if (course.getVisibility() != CourseVisibility.PUBLIC)
            throw error(ErrorCode.FORBIDDEN, "Direct enrollment requires a public course");
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED)
            throw error(ErrorCode.COURSE_NOT_OPEN, "Course is not published");
        requireAvailableSeat(course);
        if (enrollment == null) {
            enrollment = new Enrollment();
            enrollment.setCourse(course);
            enrollment.setStudent(student);
        }
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setWithdrawnAt(null);
        // Preserve original enrolledAt and all lesson progress on reactivation.
        return EnrollmentResponse.from(enrollments.save(enrollment));
    }

    public EnrollmentResponse withdraw(UUID id, UUID studentId) {
        requireUser(studentId, UserRole.STUDENT);
        Enrollment enrollment = lockedEnrollment(id);
        if (!enrollment.getStudent().getId().equals(studentId))
            throw error(ErrorCode.FORBIDDEN, "You can only withdraw your own enrollment");
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) return EnrollmentResponse.from(enrollment);
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE)
            throw error(ErrorCode.ENROLLMENT_STATE_CONFLICT, "Only active enrollments can be withdrawn");
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setWithdrawnAt(Instant.now());
        return EnrollmentResponse.from(enrollment);
    }

    public EnrollmentResponse remove(UUID id, UUID instructorId) {
        requireUser(instructorId, UserRole.INSTRUCTOR);
        Enrollment enrollment = lockedEnrollment(id);
        requireOwner(enrollment.getCourse().getId(), instructorId);
        if (enrollment.getStatus() != EnrollmentStatus.REMOVED) {
            enrollment.setStatus(EnrollmentStatus.REMOVED);
            enrollment.setRemovedAt(Instant.now());
        }
        return EnrollmentResponse.from(enrollment);
    }

    public Page<EnrollmentResponse> list(UUID actorId, UUID courseId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw error(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size between 1 and 100");
        var pageable = PageRequest.of(page, size, Sort.by("enrolledAt").descending().and(Sort.by("id")));
        if (courseId == null) {
            requireUser(actorId, UserRole.STUDENT);
            return enrollments.findByStudentId(actorId, pageable).map(EnrollmentResponse::from);
        }
        requireUser(actorId, UserRole.INSTRUCTOR);
        requireOwner(courseId, actorId);
        return enrollments.findByCourseId(courseId, pageable).map(EnrollmentResponse::from);
    }

    private Enrollment lockedEnrollment(UUID id) {
        UUID courseId = enrollments.findCourseIdById(id)
                .orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Enrollment not found"));
        lockCourse(courseId);
        return enrollments.findById(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Enrollment not found"));
    }
    private void requireAvailableSeat(Course course) {
        if (course.getCapacity() != null && enrollments.countByCourseIdAndStatusIn(course.getId(),
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED)) >= course.getCapacity())
            throw error(ErrorCode.COURSE_FULL, "Course is full");
    }
    private Course lockCourse(UUID id) {
        return courses.findByIdForUpdate(id).orElseThrow(() -> error(ErrorCode.NOT_FOUND, "Course not found"));
    }
    private User requireUser(UUID id, UserRole role) {
        User user = users.findById(id).orElseThrow(() -> error(ErrorCode.FORBIDDEN, "Account unavailable"));
        if (user.getAccountStatus() != AccountStatus.ACTIVE || user.getRole() != role)
            throw error(ErrorCode.FORBIDDEN, "Account is not permitted to perform this action");
        return user;
    }
    private void requireOwner(UUID courseId, UUID actorId) {
        if (!instructors.existsByCourseIdAndInstructorIdAndCourseRole(courseId, actorId, CourseInstructorRole.OWNER))
            throw error(ErrorCode.FORBIDDEN, "Only the course owner can manage enrollments");
    }
    private BusinessException error(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }
}
