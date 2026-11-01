package com.oep.corelms.controller;

import com.oep.corelms.dto.notification.NotificationResponse;
import com.oep.corelms.service.NotificationService;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for user in-app notification inbox, polling, and read receipts.
 */
@RestController
@RequestMapping({"/api/notifications", "/api/v1/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return notificationService.listNotifications(actor(jwt), unreadOnly, page, size);
    }

    @GetMapping({"/unread", "/unread-count"})
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        long count = notificationService.countUnread(actor(jwt));
        return Map.of("unreadCount", count);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return notificationService.markAsRead(id, actor(jwt));
    }

    @RequestMapping(value = "/read-all", method = {RequestMethod.PATCH, RequestMethod.POST})
    public Map<String, Object> markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        int count = notificationService.markAllAsRead(actor(jwt));
        return Map.of("markedCount", count, "success", true);
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Token subject must be a valid user UUID");
        }
    }
}
