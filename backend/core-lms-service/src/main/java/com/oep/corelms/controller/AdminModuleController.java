package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateModuleRequest;
import com.oep.corelms.dto.admin.ModuleResponse;
import com.oep.corelms.dto.admin.UpdateModuleRequest;
import com.oep.corelms.service.AdminModuleService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminModuleController {

    private final AdminModuleService moduleService;
    private final AuditLogService auditLogService;

    @PostMapping("/api/admin/courses/{courseId}/modules")
    public ModuleResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                  @Valid @RequestBody CreateModuleRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        ModuleResponse response = moduleService.create(courseId, request);
        auditLogService.record(adminId, "CREATE_MODULE", "MODULE", response.id(), Map.of("courseId", courseId.toString()));
        return response;
    }

    @GetMapping("/api/admin/courses/{courseId}/modules")
    public List<ModuleResponse> list(@PathVariable UUID courseId) {
        return moduleService.list(courseId);
    }

    @PatchMapping("/api/admin/modules/{moduleId}")
    public ModuleResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID moduleId,
                                  @Valid @RequestBody UpdateModuleRequest request) {
        ModuleResponse response = moduleService.update(moduleId, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_MODULE", "MODULE", moduleId, Map.of("title", response.title()));
        return response;
    }

    @DeleteMapping("/api/admin/modules/{moduleId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID moduleId) {
        moduleService.delete(moduleId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_MODULE", "MODULE", moduleId, null);
        return ResponseEntity.noContent().build();
    }
}
