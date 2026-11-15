package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminUserResponse;
import com.oep.corelms.dto.admin.CreateTeacherRequest;
import com.oep.corelms.dto.admin.CreateTeacherResponse;
import com.oep.corelms.dto.admin.UpdateUserStatusRequest;
import com.oep.corelms.service.AdminUserService;
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
import java.util.UUID;

/** Everything under /api/admin/** requires ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/teachers")
    public CreateTeacherResponse createTeacher(@Valid @RequestBody CreateTeacherRequest request) {
        return adminUserService.createTeacher(request);
    }

    @GetMapping("/users")
    public List<AdminUserResponse> listUsers(@RequestParam(required = false) UserRole role,
                                              @RequestParam(required = false) AccountStatus status) {
        return adminUserService.list(role, status);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse updateUserStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminUserService.updateStatus(id, request.status(), UUID.fromString(jwt.getSubject()));
    }
}
