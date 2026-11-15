package com.oep.corelms.controller;

import com.oep.corelms.dto.notification.NotificationResponse;
import com.oep.corelms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return notificationService.markRead(UUID.fromString(jwt.getSubject()), id);
    }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(UUID.fromString(jwt.getSubject()));
    }
}
