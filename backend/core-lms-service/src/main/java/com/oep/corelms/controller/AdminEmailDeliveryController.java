package com.oep.corelms.controller;

import com.oep.corelms.service.AdminEmailDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/email-deliveries")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminEmailDeliveryController {

    private final AdminEmailDeliveryService deliveries;

    @GetMapping
    public AdminEmailDeliveryService.DeliveryPage listFailed(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        return deliveries.listFailed(actor(jwt), page, pageSize);
    }

    @PostMapping("/{eventId}/replay")
    public AdminEmailDeliveryService.Delivery replay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return deliveries.replay(actor(jwt), eventId);
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) throw new AccessDeniedException("Authentication required");
        return UUID.fromString(jwt.getSubject());
    }
}
