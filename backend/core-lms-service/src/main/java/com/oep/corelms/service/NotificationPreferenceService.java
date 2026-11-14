package com.oep.corelms.service;

import com.oep.corelms.repository.NotificationPreferenceRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.notification.NotificationPreference;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    public static final String ALL_EVENTS = "*";
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;

    public record Preference(boolean inAppEnabled, boolean emailEnabled) {}

    @Transactional(readOnly = true)
    public Preference get(UUID userId) {
        requireActiveUser(userId);
        return preferences.findByUser_IdAndEventType(userId, ALL_EVENTS)
                .map(item -> new Preference(item.isInAppEnabled(), item.isEmailEnabled()))
                .orElse(new Preference(true, true));
    }

    @Transactional
    public Preference update(UUID userId, Preference input) {
        var user = requireActiveUser(userId);
        NotificationPreference setting = preferences.findByUser_IdAndEventType(userId, ALL_EVENTS)
                .orElseGet(() -> {
                    var created = new NotificationPreference();
                    created.setUser(user);
                    created.setEventType(ALL_EVENTS);
                    return created;
                });
        setting.setInAppEnabled(input.inAppEnabled());
        setting.setEmailEnabled(input.emailEnabled());
        preferences.save(setting);
        return input;
    }

    private com.oep.coremodel.entity.identity.User requireActiveUser(UUID userId) {
        return users.findById(userId).filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản không hoạt động"));
    }
}
