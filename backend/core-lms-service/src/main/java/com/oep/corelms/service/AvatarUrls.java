package com.oep.corelms.service;

import com.oep.corelms.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Short-lived presigned view URL of a user's avatar (null when none), for showing people in rosters and course pages. */
@Component
@RequiredArgsConstructor
public class AvatarUrls {

    private final UserProfileRepository profiles;
    private final StorageService storage;

    public String of(UUID userId) {
        return profiles.findById(userId).map(p -> p.getAvatarObjectKey()).filter(k -> k != null).map(storage::presignView).orElse(null);
    }
}
