package com.ondemandmonitoring.Consultation.domains;

import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_consultations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerConsultation extends BaseEntity {

    /**
     * Customer who started this consultation.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /**
     * Order created after the customer confirms the consultation.
     *
     * Nullable while consultation is still in progress.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Service recommended by the AI after understanding
     * the customer's monitoring requirements.
     *
     * Nullable until the AI has enough information.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_service_id")
    private Service recommendedService;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ConsultationStatus status = ConsultationStatus.ACTIVE;

    /**
     * Structured requirements extracted from the conversation.
     *
     * Example:
     * {
     *   "objective": "Identify abnormal crop areas",
     *   "scope": "Entire garden",
     *   "concern": "Poor crop growth",
     *   "detailLevel": "Detailed inspection",
     *   "frequency": "ONE_TIME"
     * }
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirement_data", columnDefinition = "jsonb")
    private String requirementData;

    /**
     * Human-readable requirement summary shown to the customer
     * before creating the Order.
     */
    @Column(name = "requirement_summary", columnDefinition = "TEXT")
    private String requirementSummary;

    /**
     * AI-generated request title used to pre-fill the create request form.
     * Customer can still edit the final Order title before submitting.
     */
    @Column(name = "request_title", length = 255)
    private String requestTitle;

    /**
     * AI-generated request description used to pre-fill the create request form.
     * Customer can still edit the final Order description before submitting.
     */
    @Column(name = "request_summary", columnDefinition = "TEXT")
    private String requestSummary;

    @Builder.Default
    @OneToMany(
            mappedBy = "consultation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ConsultationMessage> messages = new ArrayList<>();

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    protected void onCreate() {

        if (status == null) {
            status = ConsultationStatus.ACTIVE;
        }
    }
}
