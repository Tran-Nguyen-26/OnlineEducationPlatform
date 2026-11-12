package com.oep.coremodel.entity.enrollment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "heartbeat_flush_batches")
public class HeartbeatFlushBatch {
    @Id
    private UUID id;

    @Column(name = "flushed_at", nullable = false)
    private Instant flushedAt;

    @Column(name = "progress_rows", nullable = false)
    private long progressRows;

    @Column(name = "heartbeat_count", nullable = false)
    private long heartbeatCount;
}
