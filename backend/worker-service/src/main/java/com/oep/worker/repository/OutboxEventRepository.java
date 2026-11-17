package com.oep.worker.repository;

import com.oep.coremodel.entity.system.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several relay instances drain the table without blocking or double-sending. */
    @Query(value = "select * from outbox_events where status = 'PENDING' and next_attempt_at <= now() order by created_at limit :batch for update skip locked",
            nativeQuery = true)
    List<OutboxEvent> lockDueBatch(int batch);

    long countByStatus(com.oep.coremodel.enums.OutboxStatus status);
}
