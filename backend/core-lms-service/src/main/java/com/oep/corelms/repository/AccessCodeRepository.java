package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.AccessCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccessCode> findByCodeHash(String codeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccessCode> findOneById(UUID id);

    List<AccessCode> findByBatchIdOrderById(UUID batchId);
}
