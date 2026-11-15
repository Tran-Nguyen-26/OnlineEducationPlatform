package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.GenerateInviteCodeRequest;
import com.oep.corelms.dto.admin.InviteCodeResponse;
import com.oep.corelms.service.AdminInviteCodeService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/courses/{courseId}/invite-codes")
@RequiredArgsConstructor
public class AdminInviteCodeController {

    private final AdminInviteCodeService inviteCodeService;

    @PostMapping
    public InviteCodeResponse generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                        @Valid @RequestBody GenerateInviteCodeRequest request) {
        return inviteCodeService.generate(courseId, request.expiresAt(), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    public List<InviteCodeResponse> list(@PathVariable UUID courseId) {
        return inviteCodeService.list(courseId);
    }

    @PatchMapping("/{codeId}/deactivate")
    public InviteCodeResponse deactivate(@PathVariable UUID courseId, @PathVariable UUID codeId) {
        return inviteCodeService.deactivate(courseId, codeId);
    }
}
