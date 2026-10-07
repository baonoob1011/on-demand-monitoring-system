package com.ondemandmonitoring.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record QuoteDraftRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal packagePrice,
        @NotNull @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal discountAmount,
        @NotNull @Digits(integer = 19, fraction = 0) BigDecimal adjustmentAmount,
        @Size(max = 2000) String managerNote,
        Map<String, @NotNull @DecimalMin("0") @Digits(integer = 19, fraction = 0) BigDecimal> additionalUnitPrices) {}
