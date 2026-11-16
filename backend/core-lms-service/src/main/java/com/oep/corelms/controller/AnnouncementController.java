package com.oep.corelms.controller;

import com.oep.corelms.dto.social.AnnouncementResponse;
import com.oep.corelms.dto.social.PostAnnouncementRequest;
import com.oep.corelms.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/api/courses/{courseId}/announcements")
    public List<AnnouncementResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return announcementService.list(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"));
    }

    @PostMapping("/api/courses/{courseId}/announcements")
    public AnnouncementResponse post(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                      @Valid @RequestBody PostAnnouncementRequest request) {
        return announcementService.post(courseId, UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("role"),
                request.title(), request.content());
    }
}
