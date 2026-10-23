package com.oep.corelms.repository;

import com.oep.coremodel.entity.system.AuditLog;
import com.oep.coremodel.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting and querying immutable forensic audit trail records.
 * In accordance with governance and non-functional requirements:
 * - Records are append-only (no application-level updates or deletes).
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);
}
