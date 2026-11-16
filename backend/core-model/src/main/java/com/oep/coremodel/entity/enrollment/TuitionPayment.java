package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** One ledger entry of tuition paid toward an enrollment; amountDue is Course.feeAmount, not snapshotted here. */
@Getter
@Setter
@Entity
@Table(name = "tuition_payments", indexes = {@Index(name = "idx_tuition_payments_enrollment_id", columnList = "enrollment_id")})
public class TuitionPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20, columnDefinition = "varchar(20) not null default 'PAYMENT'")
    private com.oep.coremodel.enums.PaymentEntryType entryType = com.oep.coremodel.enums.PaymentEntryType.PAYMENT;

    @Column(name = "note", nullable = true, length = 255)
    private String note;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
