package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.BankQuestionResponse;
import com.oep.corelms.service.AdminQuizQuestionService;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminQuestionBankController {

    private final AdminQuizQuestionService service;
    private final AuditLogService auditLogService;

    @GetMapping("/courses/{courseId}/question-bank")
    public List<BankQuestionResponse> bank(@PathVariable UUID courseId) {
        return service.bank(courseId);
    }

    @DeleteMapping("/question-bank/{questionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        service.deleteFromBank(questionId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_BANK_QUESTION", "QUESTION", questionId, Map.of());
        return ResponseEntity.noContent().build();
    }
}
