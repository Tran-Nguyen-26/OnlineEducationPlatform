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

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list() {
        return auditLogs.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
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
