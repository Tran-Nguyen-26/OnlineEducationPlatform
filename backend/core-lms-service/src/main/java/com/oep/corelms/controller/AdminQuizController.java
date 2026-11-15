package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateQuizRequest;
import com.oep.corelms.dto.admin.QuizResponse;
import com.oep.corelms.dto.admin.UpdateQuizLockRequest;
import com.oep.corelms.dto.admin.UpdateQuizRequest;
import com.oep.corelms.service.AdminQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminQuizController {

    private final AdminQuizService quizService;

    @PostMapping("/api/admin/courses/{courseId}/quizzes")
    public QuizResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                @Valid @RequestBody CreateQuizRequest request) {
        return quizService.create(courseId, UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/api/admin/courses/{courseId}/quizzes")
    public List<QuizResponse> list(@PathVariable UUID courseId) {
        return quizService.list(courseId);
    }

    @PatchMapping("/api/admin/quizzes/{quizId}")
    public QuizResponse update(@PathVariable UUID quizId, @Valid @RequestBody UpdateQuizRequest request) {
        return quizService.update(quizId, request);
    }

    @PatchMapping("/api/admin/quizzes/{quizId}/lock")
    public QuizResponse setLocked(@PathVariable UUID quizId, @Valid @RequestBody UpdateQuizLockRequest request) {
        return quizService.setLocked(quizId, request.locked());
    }

    @DeleteMapping("/api/admin/quizzes/{quizId}")
    public ResponseEntity<Void> delete(@PathVariable UUID quizId) {
        quizService.delete(quizId);
        return ResponseEntity.noContent().build();
    }
}
