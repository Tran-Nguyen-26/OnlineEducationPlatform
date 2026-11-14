package com.oep.corelms.controller;

import com.oep.corelms.dto.ai.AiTutorDtos.AskRequest;
import com.oep.corelms.dto.ai.AiTutorDtos.AskResponse;
import com.oep.corelms.service.AiTutorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AiTutorController {
    private final AiTutorService aiTutorService;

    @PostMapping("/api/v1/ai/tutor/ask")
    public ResponseEntity<AskResponse> ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(aiTutorService.ask(UUID.fromString(jwt.getSubject()), request));
    }
}
