package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByEnrollment_Id(UUID enrollmentId);
    Optional<Certificate> findByCertificateNumber(String certificateNumber);
    List<Certificate> findByEnrollment_Student_IdOrderByIssuedAtDesc(UUID studentId);
    boolean existsByCertificateNumber(String certificateNumber);
}
