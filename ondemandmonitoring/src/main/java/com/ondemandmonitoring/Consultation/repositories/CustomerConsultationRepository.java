package com.ondemandmonitoring.Consultation.repositories;

import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerConsultationRepository
        extends JpaRepository<CustomerConsultation, String> {

    List<CustomerConsultation> findByCustomerIdOrderByCreatedAtDesc(
            String customerId
    );

    Optional<CustomerConsultation>
    findFirstByCustomerIdAndStatusOrderByCreatedAtDesc(
            String customerId,
            ConsultationStatus status
    );
}