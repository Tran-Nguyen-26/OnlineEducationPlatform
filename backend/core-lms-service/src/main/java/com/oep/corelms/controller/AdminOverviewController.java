package com.oep.corelms.controller;

import com.oep.corelms.service.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminOverviewController {
    private final AdminOverviewService overview;

    @GetMapping
    public AdminOverviewService.Overview get() {
        return overview.get();
    }
}
