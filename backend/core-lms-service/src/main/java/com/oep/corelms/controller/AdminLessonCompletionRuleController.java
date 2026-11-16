package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.LessonCompletionRuleRequest;
import com.oep.corelms.dto.admin.LessonCompletionRuleResponse;
import com.oep.corelms.service.AdminLessonCompletionRuleService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminLessonCompletionRuleController {

    private final AdminLessonCompletionRuleService ruleService;
    private final AuditLogService auditLogService;

    @GetMapping("/api/admin/lessons/{lessonId}/completion-rule")
    public LessonCompletionRuleResponse get(@PathVariable UUID lessonId) {
        return ruleService.get(lessonId);
    }

    @PutMapping("/api/admin/lessons/{lessonId}/completion-rule")
    public LessonCompletionRuleResponse upsert(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId,
                                                @Valid @RequestBody LessonCompletionRuleRequest request) {
        LessonCompletionRuleResponse response = ruleService.upsert(lessonId, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "SET_LESSON_COMPLETION_RULE", "LESSON", lessonId,
                Map.of("minimumTimeSeconds", request.minimumTimeSeconds(), "requireQuiz", request.requireQuiz(),
                        "requireAssignment", request.requireAssignment()));
        return response;
    }

    @DeleteMapping("/api/admin/lessons/{lessonId}/completion-rule")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        ruleService.delete(lessonId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_LESSON_COMPLETION_RULE", "LESSON", lessonId, null);
        return ResponseEntity.noContent().build();
    }
}
