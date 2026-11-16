package com.oep.worker.repository;

import com.oep.coremodel.entity.notification.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUser_IdAndEventType(UUID userId, String eventType);
}
