package com.oep.worker.repository;

import com.oep.coremodel.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisting and validating idempotent notifications in worker-service.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByDedupeKey(String dedupeKey);

    boolean existsByDedupeKey(String dedupeKey);

    long countByDedupeKey(String dedupeKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Notification n WHERE n.read = true AND n.createdAt < :cutoff")
    int deleteReadNotificationsOlderThan(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);
}
