package com.ondemandmonitoring.Consultation.dtos.responses;

import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CustomerConsultationResponse {

    private String id;

    private String customerId;

    private String orderId;

    private String recommendedServiceId;

    private String recommendedServiceName;

    private ConsultationStatus status;

    private String requirementData;

    private String requirementSummary;

    private String requestTitle;

    private String requestSummary;

    private Instant startedAt;

    private Instant completedAt;

    private List<ConsultationMessageResponse> messages;
}
