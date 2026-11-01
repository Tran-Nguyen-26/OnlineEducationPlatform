package com.oep.corelms.repository;

import com.oep.coremodel.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for storing and polling user notifications with indexed deduplication.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadOrderByCreatedAtDesc(UUID userId, boolean read, Pageable pageable);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(UUID userId, boolean read);

    long countByUserIdAndRead(UUID userId, boolean read);

    Optional<Notification> findByDedupeKey(String dedupeKey);

    boolean existsByDedupeKey(String dedupeKey);
}
