package com.oep.corelms.dto;

/** Computed from Course.feeAmount vs the sum of an enrollment's TuitionPayment ledger — never persisted. */
public enum PaymentStatus {
    NOT_APPLICABLE,
    UNPAID,
    PARTIAL,
    PAID
}
