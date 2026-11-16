package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.TuitionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TuitionPaymentRepository extends JpaRepository<TuitionPayment, UUID> {
    List<TuitionPayment> findByEnrollment_IdOrderByPaidAtDesc(UUID enrollmentId);
}
