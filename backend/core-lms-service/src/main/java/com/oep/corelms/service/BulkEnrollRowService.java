package com.oep.corelms.service;

import com.oep.corelms.dto.admin.BulkEnrollRowResult;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.security.RandomPasswordGenerator;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * One bulk-enroll row, in its own REQUIRES_NEW transaction — a separate bean from AdminBulkEnrollmentService
 * so the propagation actually takes effect (same-class self-invocation would bypass Spring's transaction proxy).
 */
@Service
@RequiredArgsConstructor
public class BulkEnrollRowService {

    private final CourseRepository courses;
    private final CourseInviteCodeRepository inviteCodes;
    private final UserRepository users;
    private final AuthIdentityRepository authIdentities;
    private final UserProfileRepository userProfiles;
    private final EnrollmentRepository enrollments;
    private final PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BulkEnrollRowResult processRow(UUID courseId, UUID inviteCodeId, String email) {
        Course course = courses.getReferenceById(courseId);
        CourseInviteCode inviteCode = inviteCodes.getReferenceById(inviteCodeId);

        Optional<User> existingUser = users.findByEmail(email);
        String temporaryPassword = null;
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.getRole() != UserRole.STUDENT) {
                return new BulkEnrollRowResult(email, "ERROR", "Email đã thuộc tài khoản không phải học sinh", null);
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setRole(UserRole.STUDENT);
            user.setAccountStatus(AccountStatus.ACTIVE);
            user = users.save(user);

            temporaryPassword = RandomPasswordGenerator.generate();
            AuthIdentity identity = new AuthIdentity();
            identity.setUser(user);
            identity.setProvider(AuthProvider.LOCAL);
            identity.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            identity.setMustChangePassword(true);
            authIdentities.save(identity);

            UserProfile profile = new UserProfile();
            profile.setUser(user);
            profile.setFullName(email.substring(0, email.indexOf('@')));
            userProfiles.save(profile);
        }

        Optional<Enrollment> existing = enrollments.findByCourseIdAndStudentId(courseId, user.getId());
        if (existing.isPresent() && (existing.get().getStatus() == EnrollmentStatus.ACTIVE
                || existing.get().getStatus() == EnrollmentStatus.COMPLETED
                || existing.get().getStatus() == EnrollmentStatus.WAITLISTED)) {
            return new BulkEnrollRowResult(email, "ALREADY_ENROLLED", "Đã ghi danh hoặc đang chờ", null);
        }

        boolean isFull = course.getCapacity() != null
                && enrollments.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE) >= course.getCapacity();

        Enrollment e = existing.orElseGet(Enrollment::new);
        e.setCourse(course);
        e.setStudent(user);
        e.setInviteCode(inviteCode);
        e.setStatus(isFull ? EnrollmentStatus.WAITLISTED : EnrollmentStatus.ACTIVE);
        e.setWithdrawnAt(null);
        e.setRemovedAt(null);
        enrollments.save(e);

        String status = temporaryPassword != null ? "CREATED_AND_ENROLLED" : "ENROLLED";
        String message = isFull ? "Khóa đã đầy — đưa vào danh sách chờ" : "Đã ghi danh";
        return new BulkEnrollRowResult(email, isFull ? "WAITLISTED" : status, message, temporaryPassword);
    }
}
