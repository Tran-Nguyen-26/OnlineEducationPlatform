package com.oep.corelms.repository;

import com.oep.coremodel.entity.system.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "actor")
    org.springframework.data.domain.Page<AuditLog> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "actor")
    org.springframework.data.domain.Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, org.springframework.data.domain.Pageable pageable);
}
