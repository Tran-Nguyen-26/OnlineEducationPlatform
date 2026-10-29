package com.oep.corelms.repository;

import com.oep.coremodel.entity.registration.InstructorApplication;
import com.oep.coremodel.enums.InstructorApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InstructorApplicationRepository extends JpaRepository<InstructorApplication, UUID> {

    boolean existsByEmailIgnoreCaseAndStatus(String email, InstructorApplicationStatus status);

    @Query("SELECT a FROM InstructorApplication a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:search IS NULL OR LOWER(a.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(a.organization) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<InstructorApplication> findAllWithFilter(
            @Param("status") InstructorApplicationStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
