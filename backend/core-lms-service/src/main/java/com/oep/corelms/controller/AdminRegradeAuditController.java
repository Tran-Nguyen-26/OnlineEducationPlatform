package com.oep.corelms.controller;

import com.oep.corelms.service.AdminRegradeAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/regrade-audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRegradeAuditController {
    private final AdminRegradeAuditService audit;

    @GetMapping
    public List<AdminRegradeAuditService.Regrade> list() {
        return audit.list();
    }
}
