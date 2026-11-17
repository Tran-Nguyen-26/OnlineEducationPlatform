package com.oep.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.system.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Writes an audit_logs row in the CALLER's transaction (so the trail commits or rolls back with the business change). */
@Component
@RequiredArgsConstructor
public class AuditTrail {

    @PersistenceContext
    private EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID actorId, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setActor(em.getReference(User.class, actorId));
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        try {
            entry.setMetadata(metadata == null || metadata.isEmpty() ? null : mapper.writeValueAsString(metadata));
        } catch (JsonProcessingException e) {
            entry.setMetadata(null);
        }
        em.persist(entry);
    }
}
