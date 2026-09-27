package com.ondemandmonitoring.Consultation.domains;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "consultation_prompt_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_consultation_prompt_template_key",
                columnNames = "template_key"
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationPromptTemplate extends BaseEntity {

    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
