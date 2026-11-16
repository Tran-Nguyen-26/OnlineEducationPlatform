package com.oep.corelms.controller;

import com.oep.corelms.repository.NotificationPreferenceRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.notification.NotificationPreference;
import com.oep.coremodel.event.NotificationCategory;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Per-user switches for notification categories (default: all on). System announcements and certificates always arrive. */
@RestController
@RequiredArgsConstructor
public class NotificationPreferenceController {

    public record Update(String category, boolean enabled) {}

    private final NotificationPreferenceRepository prefs;
    private final UserRepository users;

    @GetMapping("/api/users/me/notification-preferences")
    public Map<String, Boolean> get(@AuthenticationPrincipal Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        Map<String, Boolean> out = new LinkedHashMap<>();
        NotificationCategory.ALL.forEach(c -> out.put(c, true));
        prefs.findByUser_Id(id).forEach(p -> { if (out.containsKey(p.getEventType())) out.put(p.getEventType(), p.isInAppEnabled()); });
        return out;
    }

    @PutMapping("/api/users/me/notification-preferences")
    @Transactional
    public Map<String, Boolean> update(@AuthenticationPrincipal Jwt jwt, @RequestBody Update req) {
        if (!NotificationCategory.ALL.contains(req.category())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown category");
        }
        UUID id = UUID.fromString(jwt.getSubject());
        NotificationPreference p = prefs.findByUser_IdAndEventType(id, req.category()).orElseGet(() -> {
            NotificationPreference n = new NotificationPreference();
            n.setUser(users.getReferenceById(id));
            n.setEventType(req.category());
            return n;
        });
        p.setInAppEnabled(req.enabled());
        prefs.save(p);
        return get(jwt);
    }
}
