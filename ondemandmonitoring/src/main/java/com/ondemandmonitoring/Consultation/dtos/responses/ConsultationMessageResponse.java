package com.ondemandmonitoring.Consultation.dtos.responses;

import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ConsultationMessageResponse {

    private String id;

    private ConsultationSenderType senderType;

    private String message;

    private Instant createdAt;
}