package com.ondemandmonitoring.order.dto.response;

import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import com.ondemandmonitoring.order.enums.ChecklistReviewStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

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
    private int evidencePolicyVersion;
    private int minimumEvidenceCount;
}
