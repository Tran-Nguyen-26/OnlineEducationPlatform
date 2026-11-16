package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateQuizRequest;
import com.oep.corelms.dto.admin.QuizResponse;
import com.oep.corelms.dto.admin.UpdateQuizLockRequest;
import com.oep.corelms.dto.admin.UpdateQuizRequest;
import com.oep.corelms.service.AdminQuizService;
import com.oep.corelms.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminQuizController {

    private final AdminQuizService quizService;
    private final AuditLogService auditLogService;

    @PostMapping("/api/admin/courses/{courseId}/quizzes")
    public QuizResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                @Valid @RequestBody CreateQuizRequest request) {
        UUID adminId = UUID.fromString(jwt.getSubject());
        QuizResponse response = quizService.create(courseId, adminId, request);
        auditLogService.record(adminId, "CREATE_QUIZ", "QUIZ", response.id(), Map.of("title", response.title()));
        return response;
    }

    @GetMapping("/api/admin/courses/{courseId}/quizzes")
    public List<QuizResponse> list(@PathVariable UUID courseId) {
        return quizService.list(courseId);
    }

    @PatchMapping("/api/admin/quizzes/{quizId}")
    public QuizResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId,
                                @Valid @RequestBody UpdateQuizRequest request) {
        QuizResponse response = quizService.update(quizId, request);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "UPDATE_QUIZ", "QUIZ", quizId, Map.of("title", response.title()));
        return response;
    }

    @PatchMapping("/api/admin/quizzes/{quizId}/lock")
    public QuizResponse setLocked(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId,
                                   @Valid @RequestBody UpdateQuizLockRequest request) {
        QuizResponse response = quizService.setLocked(quizId, request.locked());
        auditLogService.record(UUID.fromString(jwt.getSubject()), request.locked() ? "LOCK_QUIZ" : "UNLOCK_QUIZ",
                "QUIZ", quizId, null);
        return response;
    }

    @DeleteMapping("/api/admin/quizzes/{quizId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID quizId) {
        quizService.delete(quizId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_QUIZ", "QUIZ", quizId, null);
        return ResponseEntity.noContent().build();
    }
}
