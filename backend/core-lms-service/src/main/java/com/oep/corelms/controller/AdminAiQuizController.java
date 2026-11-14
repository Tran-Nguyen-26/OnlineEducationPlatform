package com.oep.corelms.controller;

import com.oep.corelms.dto.ai.AiQuizDtos;
import com.oep.corelms.service.AiQuizDraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/ai-quiz/drafts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAiQuizController {
    private final AiQuizDraftService drafts;

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public AiQuizDtos.GeneratedBatch generate(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody AiQuizDtos.GenerateRequest request) {
        return drafts.generate(actor(jwt), request);
    }

    @GetMapping
    public List<AiQuizDtos.DraftQuestion> list(@AuthenticationPrincipal Jwt jwt,
                                               @RequestParam(required = false) UUID courseId) {
        return drafts.listDrafts(actor(jwt), courseId);
    }

    @PostMapping("/{questionId}/approve")
    public AiQuizDtos.DraftQuestion approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        return drafts.approve(actor(jwt), questionId);
    }

    @PutMapping("/{questionId}/edit-approve")
    public AiQuizDtos.DraftQuestion editAndApprove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId,
                                                    @Valid @RequestBody AiQuizDtos.ReviewRequest request) {
        return drafts.editAndApprove(actor(jwt), questionId, request);
    }

    @PostMapping("/{questionId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        drafts.reject(actor(jwt), questionId);
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
