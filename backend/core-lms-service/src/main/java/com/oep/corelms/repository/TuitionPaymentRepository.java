package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.TuitionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TuitionPaymentRepository extends JpaRepository<TuitionPayment, UUID> {
    List<TuitionPayment> findByEnrollment_IdOrderByPaidAtDesc(UUID enrollmentId);

    List<TuitionPayment> findTop200ByOrderByPaidAtDesc();

    @Query("select p.enrollment.id, sum(case when p.entryType = com.oep.coremodel.enums.PaymentEntryType.REFUND then -p.amount else p.amount end) from TuitionPayment p group by p.enrollment.id")
    List<Object[]> sumPaidByEnrollment();
}
