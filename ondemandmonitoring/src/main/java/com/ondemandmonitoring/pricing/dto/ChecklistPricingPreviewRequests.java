package com.ondemandmonitoring.pricing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class ChecklistPricingPreviewRequests {
    private ChecklistPricingPreviewRequests() {}

    /**
     * Admin what-if: unsaved candidate configuration. Null item fields inherit the stored checklist definition.
     * A null {@code items} list previews the service's stored default checklist set.
     */
    public record Admin(@NotBlank String serviceId, @DecimalMin("0") BigDecimal packageFee, String policyId,
            @Size(max = 100) List<@Valid AdminItem> items) {}

    public record AdminItem(@NotBlank String checklistId, @DecimalMin("0") BigDecimal basePrice,
            @Min(0) @Max(500) Integer includedImageCount, @Min(0) @Max(500) Integer includedVideoCount,
            @Size(max = 60) String pricingProfile,
            @Min(0) @Max(500) Integer requestedImageCount, @Min(0) @Max(500) Integer requestedVideoCount) {}

    /** Customer selection. No prices are accepted from the client, only choices and quantities. */
    public record Customer(@NotBlank String serviceId, @Size(max = 100) List<@Valid CustomerItem> items,
            @DecimalMin("0") BigDecimal areaHa, @DecimalMin("0") BigDecimal lengthKm,
            @DecimalMin("0") BigDecimal altitudeM) {
        public Customer(String serviceId, List<CustomerItem> items) {
            this(serviceId, items, null, null, null);
        }
    }

    public record CustomerItem(@Size(max = 255) String sourceChecklistId, @Size(max = 500) String content,
            @Min(0) @Max(500) Integer requestedImageCount, @Min(0) @Max(500) Integer requestedVideoCount) {}
}
