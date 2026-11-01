package com.oep.corelms.controller;

import com.oep.corelms.dto.quiz.QuizDtos;
import com.oep.corelms.service.QuizManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/quizzes", "/api/admin/quizzes"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuizController {

    private final QuizManagementService quizService;

    @GetMapping
    public QuizDtos.AdminQuizPage listAllQuizzes(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "course_id", required = false) UUID courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        return quizService.adminListAllQuizzes(userId(jwt), courseId, page, pageSize);
    }

    private UUID userId(Jwt jwt) {
        if (jwt == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
