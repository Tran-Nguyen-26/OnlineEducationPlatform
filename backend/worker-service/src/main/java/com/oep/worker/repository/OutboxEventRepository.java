package com.oep.worker.repository;

import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for polling and updating transactional outbox events.
 * Uses native PostgreSQL 'FOR UPDATE SKIP LOCKED' to support multi-worker concurrency without collision.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> pollPendingWithSkipLocked(@Param("batchSize") int batchSize);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PROCESSED' AND e.processedAt < :cutoff")
    int deleteProcessedEventsOlderThan(@Param("cutoff") java.time.Instant cutoff);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PENDING', e.retryCount = 0 WHERE e.status = 'FAILED'")
    int resetFailedEventsToPending();
}
