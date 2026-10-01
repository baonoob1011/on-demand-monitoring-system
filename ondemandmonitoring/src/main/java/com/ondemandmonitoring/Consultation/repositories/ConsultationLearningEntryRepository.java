package com.ondemandmonitoring.Consultation.repositories;

import com.ondemandmonitoring.Consultation.domains.ConsultationLearningEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationLearningEntryRepository extends JpaRepository<ConsultationLearningEntry, String> {
    boolean existsByCustomerIdAndNormalizedMessage(String customerId, String normalizedMessage);
}
