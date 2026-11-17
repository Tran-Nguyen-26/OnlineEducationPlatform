package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.dto.admin.AuditLogResponse;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.system.AuditLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Write-only trail of every Admin mutation (GAPS.md G5); controllers call {@link #record} right after a write succeeds. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(UUID actorId, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setActor(users.getReferenceById(actorId));
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setMetadata(toJson(metadata));
        auditLogs.save(entry);
    }

    /** Own transaction, so the entry survives when the caller's transaction rolls back (e.g. a rejected login). */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordIndependent(UUID actorId, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        record(actorId, action, targetType, targetId, metadata);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list() {
        return auditLogs.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public com.oep.coremodel.web.PageResponse<AuditLogResponse> page(int page, int size, String action) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        var result = (action == null || action.isBlank())
                ? auditLogs.findAllByOrderByCreatedAtDesc(pageable)
                : auditLogs.findByActionOrderByCreatedAtDesc(action, pageable);
        return com.oep.coremodel.web.PageResponse.of(result, this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(entry.getId(), entry.getActor().getId(), entry.getActor().getEmail(),
                entry.getAction(), entry.getTargetType(), entry.getTargetId(), entry.getMetadata(), entry.getCreatedAt());
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log metadata", e);
            return null;
        }
    }
}
