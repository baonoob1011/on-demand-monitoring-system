package com.ondemandmonitoring.Consultation.repositories;

import com.ondemandmonitoring.Consultation.domains.ConsultationPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationPromptTemplateRepository extends JpaRepository<ConsultationPromptTemplate, String> {

    Optional<ConsultationPromptTemplate> findByTemplateKeyAndActiveTrue(String templateKey);

    Optional<ConsultationPromptTemplate> findByTemplateKey(String templateKey);
}
