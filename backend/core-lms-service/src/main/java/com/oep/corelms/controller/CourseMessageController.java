package com.oep.corelms.controller;

import com.oep.corelms.dto.social.MessageResponse;
import com.oep.corelms.dto.social.PostMessageRequest;
import com.oep.corelms.service.CourseMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/messages")
@RequiredArgsConstructor
public class CourseMessageController {

    private final CourseMessageService messageService;

    @GetMapping
    public List<MessageResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return messageService.list(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    @PostMapping
    public MessageResponse post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                 @Valid @RequestBody PostMessageRequest request) {
        return messageService.post(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"), request.content());
    }
}
