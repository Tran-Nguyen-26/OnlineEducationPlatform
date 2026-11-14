package com.oep.worker.repository;

import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import com.oep.coremodel.enums.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.UUID;

public interface EmailNotificationEventRepository extends JpaRepository<EmailNotificationEvent, UUID> {
    boolean existsByDedupeKey(String dedupeKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailNotificationEvent e where e.status = :status and e.scheduledAt <= :now order by e.scheduledAt asc")
    java.util.List<EmailNotificationEvent> findDue(NotificationDeliveryStatus status,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now, Pageable pageable);
}
