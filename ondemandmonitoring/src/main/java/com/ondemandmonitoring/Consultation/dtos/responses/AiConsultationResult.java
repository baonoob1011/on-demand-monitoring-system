package com.ondemandmonitoring.Consultation.dtos.responses;

import com.ondemandmonitoring.Consultation.domains.ConsultationRequirements;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;

public record AiConsultationResult(
        String reply,
        ConsultationStatus requirementStatus,
        String recommendedServiceId,
        String requirementSummary,
        ConsultationRequirements requirements
) {
}