package com.ondemandmonitoring.service.domain;

import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Service extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;


    @Builder.Default
    @OneToMany(mappedBy = "recommendedService", fetch = FetchType.LAZY)
    private List<CustomerConsultation> consultations = new ArrayList<>();
}
