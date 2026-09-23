package com.ondemandmonitoring.Consultation.services;

import com.ondemandmonitoring.Consultation.dtos.requests.SendConsultationMessageRequest;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;

import java.util.UUID;

public interface CustomerConsultationService {

    /**
     * Start a new AI consultation for the currently authenticated customer.
     */
    CustomerConsultationResponse startConsultation();

    /**
     * Get consultation information and conversation history.
     */
    CustomerConsultationResponse getConsultation(String consultationId);

    /**
     * Send a customer message to an active consultation.
     *
     * AI response will be integrated later through
     * AiConsultationService + RAG.
     */
    CustomerConsultationResponse sendMessage(
            String consultationId,
            SendConsultationMessageRequest request
    );
}