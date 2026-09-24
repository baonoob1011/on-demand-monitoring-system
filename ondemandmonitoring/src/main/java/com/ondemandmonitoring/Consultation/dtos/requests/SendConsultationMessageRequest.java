package com.ondemandmonitoring.Consultation.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendConsultationMessageRequest {

    @NotBlank(message = "Message is required")
    private String message;

    @Size(max = 4000, message = "Request context is too long")
    private String requestContext;
}
