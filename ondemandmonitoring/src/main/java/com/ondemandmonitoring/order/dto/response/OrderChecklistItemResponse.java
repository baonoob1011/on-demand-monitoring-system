package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import com.ondemandmonitoring.order.enums.RequiredEvidenceType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.ondemandmonitoring.pricing.enums.ChecklistDifficulty;

@Getter
@Setter
@NoArgsConstructor
public class OrderChecklistItemResponse {
    private String id;
    private String sourceChecklistId;
    private String content;
    private int displayOrder;
    private OrderChecklistSourceType sourceType;
    private ChecklistReviewStatus reviewStatus;
    private String managerNote;
    private ChecklistDifficulty difficultySnapshot;
    private int evidencePolicyVersion;
    private int minimumEvidenceCount;
    private RequiredEvidenceType requiredEvidenceType;
    private int minimumImageCount;
    private int minimumVideoCount;
    private int pricingSnapshotVersion;
    private java.math.BigDecimal basePriceSnapshot;
    private int includedImageCountSnapshot;
    private int includedVideoCountSnapshot;
    private int requestedImageCount;
    private int requestedVideoCount;
    private Integer standardVideoDurationSnapshot;
    private String pricingProfileSnapshot;
    /** The pricing policy has no price for this checklist; the Manager quotes it. */
    private boolean managerQuoteRequired;
    private java.math.BigDecimal managerQuotedPrice;
}
