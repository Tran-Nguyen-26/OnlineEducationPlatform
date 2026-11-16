package com.oep.corelms.service;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.corelms.dto.admin.RecordPaymentRequest;
import com.oep.corelms.dto.admin.TuitionPaymentResponse;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.TuitionPaymentRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.TuitionPayment;
import com.oep.coremodel.enums.PaymentEntryType;
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
                .map(TuitionPaymentService::signed)
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
        PaymentEntryType type = request.type() == null ? PaymentEntryType.PAYMENT : request.type();
        if (type == PaymentEntryType.REFUND && request.amount().compareTo(totalPaid(enrollmentId)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Refund exceeds the amount credited to this enrollment");
        }
        payment.setEntryType(type);
        payment.setRecordedBy(users.getReferenceById(adminId));
        return toResponse(payments.saveAndFlush(payment));
    }

    private TuitionPaymentResponse toResponse(TuitionPayment p) {
        return new TuitionPaymentResponse(p.getId(), p.getAmount(), p.getNote(), p.getPaidAt(), p.getRecordedBy().getEmail(),
                p.getEntryType().name(), receiptNo(p));
    }

    /** Receipt number derived from the entry (date + id prefix) so it is stable and needs no counter table. */
    public static String receiptNo(TuitionPayment p) {
        String prefix = p.getEntryType() == PaymentEntryType.REFUND ? "HT" : p.getEntryType() == PaymentEntryType.DISCOUNT ? "MG" : "BL";
        return prefix + "-" + p.getPaidAt().atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + p.getId().toString().substring(0, 6).toUpperCase();
    }

    public static BigDecimal signed(TuitionPayment p) {
        return p.getEntryType() == PaymentEntryType.REFUND ? p.getAmount().negate() : p.getAmount();
    }
}
