package com.oep.corelms.controller;

import com.oep.corelms.service.AdminWaiverService;
import com.oep.corelms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/enrollments/{enrollmentId}/waivers")
@RequiredArgsConstructor
public class AdminWaiverController {

    public record WaiveRequest(String targetType, UUID targetId, String reason) {}

    private final AdminWaiverService service;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<AdminWaiverService.Item> items(@PathVariable UUID enrollmentId) {
        return service.items(enrollmentId);
    }

    @PostMapping
    public ResponseEntity<Void> waive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId, @RequestBody WaiveRequest req) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        service.waive(enrollmentId, req.targetType(), req.targetId(), req.reason(), adminId);
        auditLogService.record(adminId, "WAIVE_COMPLETION_ITEM", "ENROLLMENT", enrollmentId,
                Map.of("targetType", String.valueOf(req.targetType()), "targetId", String.valueOf(req.targetId())));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{waiverId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID enrollmentId, @PathVariable UUID waiverId) {
        service.remove(enrollmentId, waiverId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "REMOVE_COMPLETION_WAIVER", "ENROLLMENT", enrollmentId, Map.of());
        return ResponseEntity.noContent().build();
    }
}
