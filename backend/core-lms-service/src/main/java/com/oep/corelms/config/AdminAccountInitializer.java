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
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String adminEmail = "admin@vnu.edu.vn";
        String adminPassword = "Admin@123";

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setRole(UserRole.ADMIN);
            admin.setAccountStatus(AccountStatus.ACTIVE);
            admin = userRepository.save(admin);

            UserProfile profile = new UserProfile();
            profile.setUser(admin);
            profile.setFullName("Quản trị viên Hệ thống");
            profile.setOrganization("Online Education Platform");
            userProfileRepository.save(profile);

            AuthIdentity identity = new AuthIdentity();
            identity.setUser(admin);
            identity.setProvider(AuthProvider.LOCAL);
            identity.setPasswordHash(passwordEncoder.encode(adminPassword));
            identity.setMustChangePassword(false);
            authIdentityRepository.save(identity);

            log.info("==================================================================");
            log.info(">>> Khởi tạo tài khoản ADMIN mặc định thành công!");
            log.info(">>> Email   : {}", adminEmail);
            log.info(">>> Password: {}", adminPassword);
            log.info("==================================================================");
        } else {
            log.info("Tài khoản admin ({}) đã tồn tại trong hệ thống.", adminEmail);
        }
    }
}
