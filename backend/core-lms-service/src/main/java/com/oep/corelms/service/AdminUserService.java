package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminUserResponse;
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

    @Transactional
    public CreateTeacherResponse createTeacher(CreateTeacherRequest request) {
        if (users.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setRole(UserRole.INSTRUCTOR);
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
        profile.setFullName(request.fullName());
        userProfiles.save(profile);

        return new CreateTeacherResponse(user.getId(), user.getEmail(), request.fullName(), temporaryPassword);
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
