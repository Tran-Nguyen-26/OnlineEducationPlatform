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

    long countByStatus(InstructorApplicationStatus status);

    @Query(value = """
            SELECT a FROM InstructorApplication a WHERE
            (:status IS NULL OR a.status = :status) AND
            (CAST(:search AS string) IS NULL
             OR LOWER(a.fullName) LIKE CAST(:search AS string)
             OR LOWER(a.email) LIKE CAST(:search AS string)
             OR (a.organization IS NOT NULL AND LOWER(a.organization) LIKE CAST(:search AS string))
             OR (a.expertise IS NOT NULL AND LOWER(a.expertise) LIKE CAST(:search AS string)))
            """,
            countQuery = """
            SELECT COUNT(a) FROM InstructorApplication a WHERE
            (:status IS NULL OR a.status = :status) AND
            (CAST(:search AS string) IS NULL
             OR LOWER(a.fullName) LIKE CAST(:search AS string)
             OR LOWER(a.email) LIKE CAST(:search AS string)
             OR (a.organization IS NOT NULL AND LOWER(a.organization) LIKE CAST(:search AS string))
             OR (a.expertise IS NOT NULL AND LOWER(a.expertise) LIKE CAST(:search AS string)))
            """)
    Page<InstructorApplication> findAllWithFilter(
            @Param("status") InstructorApplicationStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
