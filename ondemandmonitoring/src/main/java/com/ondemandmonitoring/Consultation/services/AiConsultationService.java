package com.ondemandmonitoring.Consultation.services;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;

import java.util.List;

public interface AiConsultationService {

    AiConsultationResult respond(
            CustomerConsultation consultation,
            List<ConsultationMessage> history
    );
}