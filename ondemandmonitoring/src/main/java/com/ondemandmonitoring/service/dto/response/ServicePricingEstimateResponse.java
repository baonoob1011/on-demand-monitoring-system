package com.ondemandmonitoring.service.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ServicePricingEstimateResponse(
        String serviceId,
        BigDecimal servicePrice,
        List<AdditionalRequirementPrice> additionalRequirements,
        BigDecimal totalPrice
) {

    @Builder
    public record AdditionalRequirementPrice(
            String type,
            String description,
            BigDecimal additionalPrice
    ) {
    }
}
