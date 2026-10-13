package com.ondemandmonitoring.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

public record PricingBreakdown(String policyId, int policyVersion, String currency,
        BigDecimal systemCalculatedAmount, boolean customWork, boolean assessmentComplete,
        List<PricingBreakdownItem> items) {}
