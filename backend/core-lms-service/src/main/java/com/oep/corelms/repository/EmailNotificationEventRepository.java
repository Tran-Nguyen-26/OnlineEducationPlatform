package com.oep.corelms.repository;

import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmailNotificationEventRepository extends JpaRepository<EmailNotificationEvent, UUID> {

    Page<EmailNotificationEvent> findByStatusOrderByScheduledAtAsc(NotificationDeliveryStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from EmailNotificationEvent event where event.id = :id")
    Optional<EmailNotificationEvent> findByIdForUpdate(@Param("id") UUID id);
}
