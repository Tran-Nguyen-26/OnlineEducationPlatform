package com.oep.corelms.service;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.corelms.dto.admin.RecordPaymentRequest;
import com.oep.corelms.dto.admin.TuitionPaymentResponse;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.TuitionPaymentRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.TuitionPayment;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Học phí (GAPS.md "Nhóm A"): a simple payment ledger per enrollment — Admin records payments manually, no real payment gateway. */
@Service
@RequiredArgsConstructor
public class TuitionPaymentService {

    private final TuitionPaymentRepository payments;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public BigDecimal totalPaid(UUID enrollmentId) {
        return payments.findByEnrollment_IdOrderByPaidAtDesc(enrollmentId).stream()
                .map(TuitionPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PaymentStatus status(BigDecimal feeAmount, BigDecimal totalPaid) {
        if (feeAmount == null || feeAmount.signum() <= 0) return PaymentStatus.NOT_APPLICABLE;
        if (totalPaid.signum() <= 0) return PaymentStatus.UNPAID;
        return totalPaid.compareTo(feeAmount) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL;
    }

    @Transactional(readOnly = true)
    public List<TuitionPaymentResponse> list(UUID enrollmentId) {
        return payments.findByEnrollment_IdOrderByPaidAtDesc(enrollmentId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TuitionPaymentResponse record(UUID enrollmentId, UUID adminId, RecordPaymentRequest request) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        TuitionPayment payment = new TuitionPayment();
        payment.setEnrollment(enrollment);
        payment.setAmount(request.amount());
        payment.setNote(request.note());
        payment.setRecordedBy(users.getReferenceById(adminId));
        return toResponse(payments.saveAndFlush(payment));
    }

    private TuitionPaymentResponse toResponse(TuitionPayment p) {
        return new TuitionPaymentResponse(p.getId(), p.getAmount(), p.getNote(), p.getPaidAt(), p.getRecordedBy().getEmail());
    }
}
