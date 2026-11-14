package com.oep.corelms.controller;

import com.oep.corelms.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/notifications/preferences", "/api/v1/notifications/preferences"})
@RequiredArgsConstructor
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferences;

    public record UpdateRequest(@NotNull Boolean inAppEnabled, @NotNull Boolean emailEnabled) {}

    @GetMapping
    public NotificationPreferenceService.Preference get(@AuthenticationPrincipal Jwt jwt) {
        return preferences.get(actor(jwt));
    }

    @PutMapping
    public NotificationPreferenceService.Preference update(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody UpdateRequest request) {
        return preferences.update(actor(jwt), new NotificationPreferenceService.Preference(
                request.inAppEnabled(), request.emailEnabled()));
    }

    private UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
