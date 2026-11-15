package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminDashboardStatsResponse;
import com.oep.corelms.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService statsService;

    @GetMapping
    public AdminDashboardStatsResponse get() {
        return statsService.getStats();
    }
}
