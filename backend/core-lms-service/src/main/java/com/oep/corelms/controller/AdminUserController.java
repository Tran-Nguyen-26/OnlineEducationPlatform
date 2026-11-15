package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateTeacherRequest;
import com.oep.corelms.dto.admin.CreateTeacherResponse;
import com.oep.corelms.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
