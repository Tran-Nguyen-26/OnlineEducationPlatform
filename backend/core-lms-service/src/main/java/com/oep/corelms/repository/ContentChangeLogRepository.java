package com.oep.corelms.repository;

import com.oep.coremodel.entity.system.ContentChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ContentChangeLogRepository extends JpaRepository<ContentChangeLog, UUID> {
    List<ContentChangeLog> findByContent_IdOrderByChangedAtDesc(UUID contentId);

    void deleteByContent_Id(UUID contentId);
}
