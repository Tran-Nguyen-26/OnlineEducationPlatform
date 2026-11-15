package com.oep.corelms.service;

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

/** Admin-only: create a teacher account directly, with a random password the teacher must change on first login. */
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
}
