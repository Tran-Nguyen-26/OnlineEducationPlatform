package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.ReportDtos;
import com.oep.corelms.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final ReportService reportService;

    @GetMapping("/overview")
    public ReportDtos.Overview overview() {
        return reportService.overview();
    }
}
