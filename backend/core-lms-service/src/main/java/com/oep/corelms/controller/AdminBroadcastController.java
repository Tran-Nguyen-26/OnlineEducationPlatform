package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.BroadcastRequest;
import com.oep.corelms.service.AuditLogService;
import com.oep.corelms.service.BroadcastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/broadcast")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private final BroadcastService broadcastService;
    private final AuditLogService auditLogService;

    @PostMapping
    public Map<String, Integer> broadcast(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BroadcastRequest request) {
        int recipients = broadcastService.broadcast(request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "BROADCAST", "SYSTEM", UUID.randomUUID(),
                Map.of("audience", request.audience().name(), "title", request.title(), "recipients", recipients));
        return Map.of("recipients", recipients);
    }
}
