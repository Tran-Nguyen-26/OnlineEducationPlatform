package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateSubjectRequest;
import com.oep.corelms.dto.admin.SubjectResponse;
import com.oep.corelms.dto.admin.UpdateSubjectRequest;
import com.oep.corelms.service.AdminSubjectService;
import com.oep.corelms.service.AuditLogService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final AdminSubjectService subjectService;
    private final AuditLogService auditLogService;

    @PostMapping
    public SubjectResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSubjectRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        SubjectResponse response = subjectService.create(request);
        auditLogService.record(adminId, "CREATE_SUBJECT", "SUBJECT", response.id(), Map.of("name", response.name()));
        return response;
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjectService.list();
    }

    @PatchMapping("/{id}")
    public SubjectResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @Valid @RequestBody UpdateSubjectRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        SubjectResponse response = subjectService.update(id, request);
        auditLogService.record(adminId, "UPDATE_SUBJECT", "SUBJECT", id, Map.of("name", response.name()));
        return response;
    }
}
