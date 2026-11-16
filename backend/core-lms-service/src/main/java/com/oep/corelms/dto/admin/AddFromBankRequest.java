package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record AddFromBankRequest(@NotNull UUID questionId, int position, @Positive BigDecimal points) {}
