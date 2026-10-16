package com.ondemandmonitoring.pricing.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Explainable price of a service or of a customer's checklist selection.
 * {@code total = packageFee + checklistSubtotal}, where {@code packageFee} is 0 when {@code priceSource} is CHECKLISTS (the default
 * price comes only from the checklists) and the legacy catalog price when it is LEGACY_BASE_PRICE (no default checklist is
 * priced yet). Customer previews may include the shared area, length and altitude context; each checklist decides which
 * dimensions apply. Other order-level factors are added by the Manager quote.
 */
public record ServicePricePreviewResponse(String serviceId, String serviceName, String currency, String policyId,
        Integer policyVersion, boolean draftPolicy, String priceSource, BigDecimal packageFee, List<ChecklistPriceBreakdown> checklists,
        BigDecimal checklistSubtotal, BigDecimal total, boolean complete, List<String> warnings, boolean quotePending) {

    public ServicePricePreviewResponse(String serviceId, String serviceName, String currency, String policyId,
            Integer policyVersion, boolean draftPolicy, String priceSource, BigDecimal packageFee, List<ChecklistPriceBreakdown> checklists,
            BigDecimal checklistSubtotal, BigDecimal total, boolean complete, List<String> warnings) {
        this(serviceId, serviceName, currency, policyId, policyVersion, draftPolicy, priceSource, packageFee, checklists,
                checklistSubtotal, total, complete, warnings, checklists.stream().anyMatch(ChecklistPriceBreakdown::quotePending));
    }
}
