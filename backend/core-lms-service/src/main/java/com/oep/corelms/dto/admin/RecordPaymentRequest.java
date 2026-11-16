package com.oep.corelms.dto.admin;

import com.oep.coremodel.enums.PaymentEntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** type defaults to PAYMENT; DISCOUNT = fee waiver, REFUND = money returned (cannot exceed the net amount credited). */
public record RecordPaymentRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String note,
        PaymentEntryType type) {
}
