package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.ChecklistExecutionStatus;
import com.ondemandmonitoring.mission.enums.ChecklistAssessmentStatus;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import java.time.Instant;
import lombok.Data;

@Data
public class ChecklistExecutionResponse {
    private String id;
    private String orderChecklistItemId;
    private String content;
    private int displayOrder;
    private OrderChecklistSourceType sourceType;
    private ChecklistExecutionStatus executionStatus;
    private ChecklistAssessmentStatus assessmentStatus;
    private String observation;
    private String unableToVerifyReason;
    private Instant startedAt;
    private Instant completedAt;
    private String lastModifiedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
