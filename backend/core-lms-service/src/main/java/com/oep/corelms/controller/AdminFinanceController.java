package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.FinanceDtos;
import com.oep.corelms.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/finance")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final FinanceService financeService;

    @GetMapping("/summary")
    public FinanceDtos.Summary summary() {
        return financeService.summary();
    }

    @GetMapping("/by-course")
    public List<FinanceDtos.CourseRow> byCourse() {
        return financeService.byCourse();
    }

    @GetMapping("/outstanding")
    public List<FinanceDtos.OutstandingRow> outstanding() {
        return financeService.outstanding();
    }

    @GetMapping("/payments")
    public List<FinanceDtos.PaymentRow> payments() {
        return financeService.recentPayments();
    }
}
