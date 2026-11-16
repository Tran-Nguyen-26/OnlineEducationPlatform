package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AuditLogResponse;
import com.oep.corelms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public List<AuditLogResponse> list() {
        return auditLogService.list();
    }
}
