package com.ondemandmonitoring.service.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Entity
@Table(
        name = "service_requirement_suggestions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_service_requirement_suggestion",
                columnNames = {"service_id", "category", "label"}
        )
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceRequirementSuggestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    Service service;

    @Column(name = "category", nullable = false, length = 100)
    String category;

    @Column(name = "label", nullable = false, length = 255)
    String label;

    @Column(name = "message", nullable = false, length = 500)
    String message;

    @Column(name = "sort_order", nullable = false)
    Integer sortOrder = 0;

    @Column(name = "source", nullable = false, length = 50)
    String source = "SEED";

    @Column(name = "usage_count", nullable = false)
    Long usageCount = 0L;

    @Column(name = "active", nullable = false)
    Boolean active = true;
}
