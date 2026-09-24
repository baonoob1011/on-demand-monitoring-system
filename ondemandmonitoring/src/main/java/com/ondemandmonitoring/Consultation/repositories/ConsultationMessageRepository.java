package com.ondemandmonitoring.Consultation.repositories;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsultationMessageRepository
        extends JpaRepository<ConsultationMessage, String> {

    List<ConsultationMessage>
    findByConsultationIdOrderByCreatedAtAsc(String consultationId);
}