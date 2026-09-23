package com.ondemandmonitoring.Consultation.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendConsultationMessageRequest {

    @NotBlank(message = "Message is required")
    private String message;
}