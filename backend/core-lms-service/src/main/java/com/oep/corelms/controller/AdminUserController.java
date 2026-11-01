package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminUserDetailResponse;
import com.oep.corelms.dto.admin.AdminUserItemResponse;
import com.oep.corelms.dto.admin.AdminUserStatsResponse;
import com.oep.corelms.dto.admin.UpdateUserStatusRequest;
import com.oep.corelms.service.AdminUserService;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Page<AdminUserItemResponse>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 8, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UserRole userRole = null;
        if (role != null && !role.trim().isEmpty() && !role.equalsIgnoreCase("ALL")) {
            try {
                userRole = UserRole.valueOf(role.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid or empty role enum value
            }
        }

        AccountStatus accountStatus = null;
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            try {
                accountStatus = AccountStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid or empty status enum value
            }
        }

        Page<AdminUserItemResponse> response = adminUserService.getUsers(search, userRole, accountStatus, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminUserStatsResponse> getUserStats() {
        AdminUserStatsResponse stats = adminUserService.getUserStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable UUID id) {
        AdminUserDetailResponse detail = adminUserService.getUserDetail(id);
        return ResponseEntity.ok(detail);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UUID adminId = (jwt != null && jwt.getSubject() != null) ? UUID.fromString(jwt.getSubject()) : null;
        AdminUserDetailResponse updated = adminUserService.updateUserStatus(id, request.status(), request.reason(), adminId);

        String statusLabel = request.status() == AccountStatus.ACTIVE ? "Mở khóa" : "Khóa";
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", statusLabel + " tài khoản người dùng thành công.",
                "data", updated
        ));
    }
}
