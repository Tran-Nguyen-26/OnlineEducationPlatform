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
    public com.oep.coremodel.web.PageResponse<AuditLogResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "25") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String action) {
        return auditLogService.page(page, size, action);
    }
}
