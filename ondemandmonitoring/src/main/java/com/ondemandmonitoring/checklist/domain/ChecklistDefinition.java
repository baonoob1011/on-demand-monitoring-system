package com.ondemandmonitoring.checklist.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "checklist_definitions", uniqueConstraints =
        @UniqueConstraint(name = "uk_checklist_normalized_content", columnNames = "normalized_content"))
@Getter
@Setter
@NoArgsConstructor
public class ChecklistDefinition extends BaseEntity {
    @Column(nullable = false, length = 500)
    private String content;
    @Column(name = "normalized_content", nullable = false, length = 1000)
    private String normalizedContent;
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
