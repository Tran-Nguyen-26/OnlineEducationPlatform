package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminUserDetailResponse;
import com.oep.corelms.dto.admin.AdminUserResponse;
import com.oep.corelms.dto.admin.CreateStudentRequest;
import com.oep.corelms.dto.admin.PageResponse;
import com.oep.corelms.dto.student.ProfileResponse;
import com.oep.corelms.dto.student.UpdateProfileRequest;
import com.oep.corelms.service.ProfileService;
import com.oep.corelms.dto.admin.CreateTeacherRequest;
import com.oep.corelms.dto.admin.CreateTeacherResponse;
import com.oep.corelms.dto.admin.ResetLinkResponse;
import com.oep.corelms.dto.admin.UpdateUserStatusRequest;
import com.oep.corelms.service.AdminUserService;
import com.oep.corelms.service.AuditLogService;
import com.oep.corelms.service.AuthService;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything under /api/admin/** requires ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final ProfileService profileService;
    private final com.oep.corelms.service.DataPrivacyService dataPrivacyService;

    @PostMapping("/teachers")
    public CreateTeacherResponse createTeacher(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTeacherRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        CreateTeacherResponse response = adminUserService.createTeacher(request);
        auditLogService.record(adminId, "CREATE_TEACHER", "USER", response.userId(), Map.of("email", response.email()));
        return response;
    }

    @PostMapping("/students")
    public CreateTeacherResponse createStudent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStudentRequest request) {
        CreateTeacherResponse response = adminUserService.createStudent(request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "CREATE_STUDENT", "USER", response.userId(), Map.of("email", response.email()));
        return response;
    }

    @GetMapping("/users/search")
    public PageResponse<AdminUserResponse> searchUsers(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) UserRole role,
                                                        @RequestParam(required = false) AccountStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return adminUserService.search(q, role, status, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailResponse userDetail(@PathVariable UUID id) {
        return adminUserService.detail(id);
    }

    @PatchMapping("/users/{id}/profile")
    public ProfileResponse updateUserProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.update(id, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_USER_PROFILE", "USER", id, Map.of("fullName", request.fullName()));
        return response;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers(@RequestParam(required = false) UserRole role,
                                              @RequestParam(required = false) AccountStatus status) {
        return adminUserService.list(role, status);
    }

    /** Right to erasure: removes identity data, keeps academic records. Irreversible. */
    @org.springframework.web.bind.annotation.PostMapping("/users/{id}/anonymize")
    public Map<String, String> anonymize(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        String placeholder = dataPrivacyService.anonymize(id, adminId);
        auditLogService.record(adminId, "ANONYMIZE_USER", "USER", id, null);
        return Map.of("email", placeholder);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse updateUserStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserStatusRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        AdminUserResponse response = adminUserService.updateStatus(id, request.status(), adminId);
        auditLogService.record(adminId, "UPDATE_USER_STATUS", "USER", id, Map.of("status", request.status().name()));
        return response;
    }

    @PostMapping("/users/{id}/reset-password-link")
    public ResetLinkResponse generateResetLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ResetLinkResponse response = authService.adminGenerateResetToken(id);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "GENERATE_RESET_LINK", "USER", id, null);
        return response;
    }
}
