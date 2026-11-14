package com.oep.corelms.controller;

import com.oep.corelms.service.LearnerDashboardService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class LearnerDashboardController {
    private final LearnerDashboardService service;

    @GetMapping("/summary")
    public LearnerDashboardService.Summary summary(@AuthenticationPrincipal Jwt jwt) {
        try {
            return service.summary(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Token subject phải là UUID người dùng");
        }
    }
}
