package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminUserDetailResponse;
import com.oep.corelms.dto.admin.AdminUserResponse;
import com.oep.corelms.dto.admin.CreateStudentRequest;
import com.oep.corelms.dto.admin.PageResponse;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.oep.corelms.dto.admin.CreateTeacherRequest;
import com.oep.corelms.dto.admin.CreateTeacherResponse;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.security.RandomPasswordGenerator;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Admin-only: create teacher accounts, list/filter all users, toggle active/disabled. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final AuthIdentityRepository authIdentities;
    private final UserProfileRepository userProfiles;
    private final PasswordEncoder passwordEncoder;
    private final EnrollmentRepository enrollments;
    private final CourseInstructorRepository courseInstructors;
    private final TuitionPaymentService tuitionPaymentService;

    @Transactional
    public CreateTeacherResponse createTeacher(CreateTeacherRequest request) {
        return createAccount(request.email(), request.fullName(), UserRole.INSTRUCTOR);
    }

    private CreateTeacherResponse createAccount(String email, String fullName, UserRole role) {
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = users.save(user);

        String temporaryPassword = RandomPasswordGenerator.generate();
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        identity.setMustChangePassword(true);
        authIdentities.save(identity);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(fullName);
        userProfiles.save(profile);

        return new CreateTeacherResponse(user.getId(), user.getEmail(), fullName, temporaryPassword);
    }

    /** Same temporary-password flow as teachers: shown once to the admin, user must change it at first login. */
    @Transactional
    public CreateTeacherResponse createStudent(CreateStudentRequest request) {
        return createAccount(request.email(), request.fullName(), UserRole.STUDENT);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String q, UserRole role, AccountStatus status, int page, int size) {
        String like = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase() + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(users.search(role, status, like, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserProfile p = userProfiles.findById(id).orElse(null);
        var enrollmentRows = enrollments.findByStudentIdOrderByEnrolledAtDesc(id).stream().map(e -> {
            var fee = e.getCourse().getFeeAmount();
            var paid = tuitionPaymentService.totalPaid(e.getId());
            return new AdminUserDetailResponse.EnrollmentSummary(e.getId(), e.getCourse().getId(), e.getCourse().getTitle(),
                    e.getStatus().name(), e.getEnrolledAt(), fee, paid, tuitionPaymentService.status(fee, paid));
        }).toList();
        var assigned = courseInstructors.findByInstructor_IdOrderByAddedAtDesc(id).stream()
                .map(ci -> new AdminUserDetailResponse.AssignedCourse(ci.getCourse().getId(), ci.getCourse().getTitle(),
                        ci.getCourse().getLifecycleStatus().name())).toList();
        return new AdminUserDetailResponse(user.getId(), user.getEmail(), user.getRole(), user.getAccountStatus(), user.getCreatedAt(),
                p == null ? null : p.getFullName(), p == null ? null : p.getPhone(), p == null ? null : p.getDateOfBirth(),
                p == null ? null : p.getOrganization(), p == null ? null : p.getBio(), enrollmentRows, assigned);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(UserRole roleFilter, AccountStatus statusFilter) {
        List<User> result;
        if (roleFilter != null && statusFilter != null) {
            result = users.findByRoleAndAccountStatus(roleFilter, statusFilter);
        } else if (roleFilter != null) {
            result = users.findByRole(roleFilter);
        } else if (statusFilter != null) {
            result = users.findByAccountStatus(statusFilter);
        } else {
            result = users.findAll();
        }
        return result.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID targetUserId, AccountStatus newStatus, UUID actingAdminId) {
        if (targetUserId.equals(actingAdminId)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF, "Admin cannot change their own account status");
        }
        User user = users.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        user.setAccountStatus(newStatus);
        return toResponse(users.save(user));
    }

    private AdminUserResponse toResponse(User user) {
        String fullName = userProfiles.findById(user.getId()).map(UserProfile::getFullName).orElse(null);
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getAccountStatus(),
                fullName, user.getCreatedAt());
    }
}
