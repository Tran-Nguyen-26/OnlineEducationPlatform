package com.oep.corelms.config;

import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the very first ADMIN account on an empty DB, since there is no other way to get in
 * (only admins can create teacher accounts, and students self-register as STUDENT). Dev-only
 * convenience, same spirit as the other dev-only shortcuts called out in CLAUDE.md: a real
 * deployment should rotate/seed this out of band instead of relying on a hardcoded default.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final AuthIdentityRepository authIdentities;
    private final UserProfileRepository userProfiles;
    private final PasswordEncoder passwordEncoder;

    @Value("${oep.admin.seed.email:admin@oep.local}")
    private String seedEmail;

    @Value("${oep.admin.seed.password:Admin@12345}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(UserRole.ADMIN)) {
            return;
        }

        User admin = new User();
        admin.setEmail(seedEmail);
        admin.setRole(UserRole.ADMIN);
        admin.setAccountStatus(AccountStatus.ACTIVE);
        admin = users.save(admin);

        AuthIdentity identity = new AuthIdentity();
        identity.setUser(admin);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setPasswordHash(passwordEncoder.encode(seedPassword));
        identity.setMustChangePassword(true);
        authIdentities.save(identity);

        UserProfile profile = new UserProfile();
        profile.setUser(admin);
        profile.setFullName("Center Admin");
        userProfiles.save(profile);

        log.warn("Seeded default ADMIN account: email={}, password={} (must change on first login)",
                seedEmail, seedPassword);
    }
}
