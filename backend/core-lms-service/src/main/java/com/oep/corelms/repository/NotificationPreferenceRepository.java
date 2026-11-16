package com.oep.corelms.repository;

import com.oep.coremodel.entity.notification.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByUser_Id(UUID userId);

    Optional<NotificationPreference> findByUser_IdAndEventType(UUID userId, String eventType);
}
