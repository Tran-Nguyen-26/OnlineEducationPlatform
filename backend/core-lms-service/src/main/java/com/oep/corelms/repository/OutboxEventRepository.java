package com.oep.corelms.repository;

import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    long countByStatus(OutboxStatus status);

    @Query("select min(o.createdAt) from OutboxEvent o where o.status = com.oep.coremodel.enums.OutboxStatus.PENDING")
    Instant oldestPending();

    @Modifying
    @Query("update OutboxEvent o set o.status = com.oep.coremodel.enums.OutboxStatus.PENDING, o.retryCount = 0, o.nextAttemptAt = current_timestamp, o.lastError = null "
            + "where o.status = com.oep.coremodel.enums.OutboxStatus.FAILED")
    int resetFailed();
}
