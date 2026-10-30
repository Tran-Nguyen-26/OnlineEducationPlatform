package com.oep.corelms.service;

import com.oep.corelms.dto.instructor.InstructorApplicationRequest;
import com.oep.corelms.dto.instructor.InstructorApplicationResponse;
import com.oep.corelms.dto.instructor.InstructorApplicationStatsResponse;
import com.oep.corelms.dto.instructor.RejectApplicationRequest;
import com.oep.corelms.repository.AuthIdentityRepository;
import com.oep.corelms.repository.InstructorApplicationRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.util.PasswordGenerator;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.entity.registration.InstructorApplication;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.AuthProvider;
import com.oep.coremodel.enums.InstructorApplicationStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.event.InstructorApplicationApprovedEvent;
import com.oep.coremodel.event.InstructorApplicationRejectedEvent;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstructorApplicationService {

    private final InstructorApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.frontend.login-url:http://localhost:3000/login}")
    private String loginUrl;

    @Transactional
    public InstructorApplicationResponse submitApplication(InstructorApplicationRequest request) {
        String email = request.email().trim().toLowerCase();

        // Check if email already has an account
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN,
                    "Email " + email + " đã được đăng ký trên hệ thống. Vui lòng đăng nhập hoặc sử dụng email khác.");
        }

        // Check if there is already a PENDING application
        if (applicationRepository.existsByEmailIgnoreCaseAndStatus(email, InstructorApplicationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PENDING,
                    "Email " + email + " hiện đang có một hồ sơ chờ xét duyệt. Quản trị viên đang xử lý, vui lòng không gửi lại.");
        }

        InstructorApplication application = new InstructorApplication();
        application.setEmail(email);
        application.setFullName(request.fullName().trim());
        application.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        application.setOrganization(request.organization() != null && !request.organization().isBlank() ? request.organization().trim() : null);
        application.setJobTitle(request.jobTitle() != null && !request.jobTitle().isBlank() ? request.jobTitle().trim() : null);
        application.setExpertise(request.expertise() != null && !request.expertise().isBlank() ? request.expertise().trim() : null);
        application.setYearsOfExperience(request.yearsOfExperience());
        application.setProfessionalBio(request.professionalBio() != null && !request.professionalBio().isBlank() ? request.professionalBio().trim() : null);
        application.setMotivation(request.motivation() != null && !request.motivation().isBlank() ? request.motivation().trim() : null);
        application.setStatus(InstructorApplicationStatus.PENDING);
        application.setSubmittedAt(Instant.now());

        InstructorApplication saved = applicationRepository.save(application);
        log.info("New instructor application submitted id={} email={}", saved.getId(), saved.getEmail());

        return InstructorApplicationResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<InstructorApplicationResponse> getApplications(InstructorApplicationStatus status, String search, Pageable pageable) {
        String sanitizedSearch = (search != null && !search.isBlank())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        return applicationRepository.findAllWithFilter(status, sanitizedSearch, pageable)
                .map(InstructorApplicationResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public InstructorApplicationStatsResponse getApplicationStats() {
        long total = applicationRepository.count();
        long pending = applicationRepository.countByStatus(InstructorApplicationStatus.PENDING);
        long approved = applicationRepository.countByStatus(InstructorApplicationStatus.APPROVED);
        long rejected = applicationRepository.countByStatus(InstructorApplicationStatus.REJECTED);
        return new InstructorApplicationStatsResponse(total, pending, approved, rejected);
    }

    @Transactional(readOnly = true)
    public InstructorApplicationResponse getApplicationDetail(UUID id) {
        InstructorApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn đăng ký giảng viên với ID: " + id));
        return InstructorApplicationResponse.fromEntity(application);
    }

    @Transactional
    public Map<String, Object> approveApplication(UUID id, UUID adminId) {
        InstructorApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn đăng ký giảng viên với ID: " + id));

        if (application.getStatus() != InstructorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_REVIEWED,
                    "Đơn đăng ký này đã được xét duyệt trước đó với trạng thái: " + application.getStatus());
        }

        String email = application.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_TAKEN,
                    "Không thể tạo tài khoản: Email " + email + " đã tồn tại trong hệ thống.");
        }

        User adminUser = null;
        if (adminId != null) {
            adminUser = userRepository.findById(adminId).orElse(null);
        }

        // 1. Generate secure random password (12 characters)
        String temporaryPassword = PasswordGenerator.generateSecurePassword();

        // 2. Create User
        User user = new User();
        user.setEmail(email);
        user.setRole(UserRole.INSTRUCTOR);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);

        // 3. Create UserProfile
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(application.getFullName());
        profile.setPhone(application.getPhone());
        profile.setOrganization(application.getOrganization());
        profile.setBio(application.getProfessionalBio());
        userProfileRepository.save(profile);

        // 4. Create AuthIdentity with mustChangePassword = true
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        identity.setMustChangePassword(true);
        authIdentityRepository.save(identity);

        // 5. Update Application status
        application.setStatus(InstructorApplicationStatus.APPROVED);
        application.setReviewedBy(adminUser);
        application.setReviewedAt(Instant.now());
        applicationRepository.save(application);

        final UUID createdUserId = user.getId();
        final String applicantEmail = application.getEmail();
        final String applicantName = application.getFullName();

        // 6. Publish Event via RabbitMQ after DB commit
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishApprovalEvent(id, createdUserId, applicantEmail, applicantName, temporaryPassword);
                }
            });
        } else {
            publishApprovalEvent(id, createdUserId, applicantEmail, applicantName, temporaryPassword);
        }

        log.info("Approved instructor application id={} created userId={}", id, createdUserId);

        return Map.of(
                "applicationId", id,
                "userId", createdUserId,
                "email", applicantEmail,
                "fullName", applicantName,
                "role", UserRole.INSTRUCTOR.name(),
                "status", InstructorApplicationStatus.APPROVED.name(),
                "reviewedAt", application.getReviewedAt()
        );
    }

    @Transactional
    public InstructorApplicationResponse rejectApplication(UUID id, UUID adminId, RejectApplicationRequest request) {
        InstructorApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn đăng ký giảng viên với ID: " + id));

        if (application.getStatus() != InstructorApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_REVIEWED,
                    "Đơn đăng ký này đã được xét duyệt trước đó với trạng thái: " + application.getStatus());
        }

        User adminUser = null;
        if (adminId != null) {
            adminUser = userRepository.findById(adminId).orElse(null);
        }

        application.setStatus(InstructorApplicationStatus.REJECTED);
        application.setRejectionReason(request.rejectionReason().trim());
        application.setReviewedBy(adminUser);
        application.setReviewedAt(Instant.now());
        InstructorApplication updated = applicationRepository.save(application);

        final String applicantEmail = application.getEmail();
        final String applicantName = application.getFullName();
        final String reason = application.getRejectionReason();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishRejectionEvent(id, applicantEmail, applicantName, reason);
                }
            });
        } else {
            publishRejectionEvent(id, applicantEmail, applicantName, reason);
        }

        log.info("Rejected instructor application id={}", id);
        return InstructorApplicationResponse.fromEntity(updated);
    }

    private void publishApprovalEvent(UUID applicationId, UUID userId, String email, String fullName, String password) {
        try {
            InstructorApplicationApprovedEvent event = new InstructorApplicationApprovedEvent(
                    applicationId,
                    userId,
                    email,
                    fullName,
                    password,
                    loginUrl,
                    Instant.now()
            );
            rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_INSTRUCTOR_APPROVED, event);
            log.info("Published InstructorApplicationApprovedEvent for applicationId={}", applicationId);
        } catch (Exception e) {
            log.error("Failed to publish InstructorApplicationApprovedEvent for applicationId={}: {}", applicationId, e.getMessage(), e);
        }
    }

    private void publishRejectionEvent(UUID applicationId, String email, String fullName, String reason) {
        try {
            InstructorApplicationRejectedEvent event = new InstructorApplicationRejectedEvent(
                    applicationId,
                    email,
                    fullName,
                    reason,
                    Instant.now()
            );
            rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_INSTRUCTOR_REJECTED, event);
            log.info("Published InstructorApplicationRejectedEvent for applicationId={}", applicationId);
        } catch (Exception e) {
            log.error("Failed to publish InstructorApplicationRejectedEvent for applicationId={}: {}", applicationId, e.getMessage(), e);
        }
    }
}
