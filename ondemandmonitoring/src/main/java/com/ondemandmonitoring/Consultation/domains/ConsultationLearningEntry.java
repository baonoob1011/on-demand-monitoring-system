package com.ondemandmonitoring.Consultation.domains;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.service.domain.Service;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Entity
@Table(name = "consultation_learning_entries")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConsultationLearningEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id")
    CustomerConsultation consultation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    Service service;

    @Column(name = "customer_id", length = 100)
    String customerId;

    @Column(name = "raw_message", nullable = false, length = 1000)
    String rawMessage;

    @Column(name = "normalized_message", nullable = false, length = 1000)
    String normalizedMessage;

    @Column(name = "promoted_to_suggestion", nullable = false)
    Boolean promotedToSuggestion = false;
}
