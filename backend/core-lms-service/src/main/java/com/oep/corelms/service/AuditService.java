package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.system.AuditLog;
import com.oep.coremodel.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing immutable system audit trail records.
 * <p>
 * This service provides cross-module audit logging for all administrative,
 * governance, security, and high-impact operations across the platform.
 * <p>
 * Key Architectural & Reliability Guarantees:
 * <ul>
 *   <li><b>Fault Isolation:</b> Audit logging executes with {@link Propagation#REQUIRES_NEW}
 *       and traps exceptions internally so an audit failure never causes the primary business
 *       transaction to fail.</li>
 *   <li><b>Immutability:</b> Audit logs are strictly append-only (no application-level UPDATE or DELETE).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records an immutable forensic audit event.
     * <p>
     * If audit recording fails due to database errors or serialization issues,
     * the error is caught and logged as a WARNING, guaranteeing that the caller's
     * main business transaction remains unaffected.
     *
     * @param actorId    UUID of the user performing the action (must not be null)
     * @param action     The classified {@link AuditAction} enum constant (must not be null)
     * @param targetType The domain entity name affected (e.g., "COURSE", "USER", "QUIZ")
     * @param targetId   UUID of the entity target (must not be null)
     * @param metadata   Optional contextual object or map to serialize into JSONB metadata
     * @return The saved {@link AuditLog} instance, or null if recording failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(UUID actorId, AuditAction action, String targetType, UUID targetId, Object metadata) {
        if (actorId == null || action == null || targetType == null || targetType.isBlank() || targetId == null) {
            log.warn("Skipping audit log recording: mandatory parameter is null or blank (actorId={}, action={}, targetType={}, targetId={})",
                    actorId, action, targetType, targetId);
            return null;
        }

        try {
            User actor = userRepository.getReferenceById(actorId);

            String jsonMetadata = null;
            if (metadata != null) {
                if (metadata instanceof String str) {
                    jsonMetadata = str;
                } else {
                    jsonMetadata = objectMapper.writeValueAsString(metadata);
                }
            }

            AuditLog logEntry = new AuditLog();
            logEntry.setActor(actor);
            logEntry.setAction(action);
            logEntry.setTargetType(targetType.trim());
            logEntry.setTargetId(targetId);
            logEntry.setMetadata(jsonMetadata);

            AuditLog saved = auditLogRepository.save(logEntry);
            log.info("Recorded audit log [id={}, action={}, actor={}, target={}:{}]",
                    saved.getId(), action, actorId, targetType, targetId);
            return saved;
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log metadata for action: {}, target: {}:{}. Reason: {}",
                    action, targetType, targetId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Audit recording failed for action: {}, target: {}:{}. Business transaction will proceed normally. Error: {}",
                    action, targetType, targetId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Convenience overload for recording audit events without extra metadata.
     *
     * @param actorId    UUID of the user performing the action
     * @param action     The classified {@link AuditAction}
     * @param targetType The domain entity name affected
     * @param targetId   UUID of the entity target
     * @return The saved {@link AuditLog} instance, or null if recording failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(UUID actorId, AuditAction action, String targetType, UUID targetId) {
        return record(actorId, action, targetType, targetId, null);
    }

    /**
     * Retrieves paginated audit trail records for a specific actor, ordered by creation time descending.
     *
     * @param actorId  User UUID of the actor
     * @param pageable Pagination and sorting specifications
     * @return Page of matching audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsByActor(UUID actorId, Pageable pageable) {
        return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
    }

    /**
     * Retrieves audit trail records for a specific actor, ordered by creation time descending.
     *
     * @param actorId User UUID of the actor
     * @return List of matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByActor(UUID actorId) {
        return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId);
    }

    /**
     * Retrieves paginated audit trail records for a specific target entity, ordered by creation time descending.
     *
     * @param targetType Type name of the target
     * @param targetId   UUID of the target
     * @param pageable   Pagination and sorting specifications
     * @return Page of matching audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsByTarget(String targetType, UUID targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }

    /**
     * Retrieves audit trail records for a specific target entity, ordered by creation time descending.
     *
     * @param targetType Type name of the target
     * @param targetId   UUID of the target
     * @return List of matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByTarget(String targetType, UUID targetId) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    /**
     * Retrieves paginated audit trail records for a specific audit action, ordered by creation time descending.
     *
     * @param action   Classified audit action
     * @param pageable Pagination and sorting specifications
     * @return Page of matching audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getLogsByAction(AuditAction action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    /**
     * Retrieves audit trail records for a specific audit action, ordered by creation time descending.
     *
     * @param action Classified audit action
     * @return List of matching audit logs
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByAction(AuditAction action) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action);
    }
}
