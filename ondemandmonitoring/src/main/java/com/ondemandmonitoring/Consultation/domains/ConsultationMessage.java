package com.ondemandmonitoring.Consultation.domains;

import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "consultation_messages",
        indexes = {
                @Index(
                        name = "idx_consultation_messages_consultation_id",
                        columnList = "consultation_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private CustomerConsultation consultation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private ConsultationSenderType senderType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
}