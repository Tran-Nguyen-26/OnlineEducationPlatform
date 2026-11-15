package com.oep.worker.repository;

import com.oep.coremodel.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsByDedupeKey(String dedupeKey);
}
