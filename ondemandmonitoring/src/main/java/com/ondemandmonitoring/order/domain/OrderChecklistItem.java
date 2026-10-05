package com.ondemandmonitoring.order.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.order.enums.OrderChecklistSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_checklist_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_checklist_source", columnNames = {"order_id", "source_checklist_id"}),
        @UniqueConstraint(name = "uk_checklist_item_id_order", columnNames = {"id", "order_id"})},
        indexes = @Index(name = "ix_order_checklist_order", columnList = "order_id,display_order,id"))
@org.hibernate.annotations.Check(constraints = "display_order >= 0 and ((source_type = 'SERVICE_TEMPLATE' and source_checklist_id is not null) or (source_type = 'CUSTOMER_CUSTOM' and source_checklist_id is null))")
@Getter
@Setter
@NoArgsConstructor
public class OrderChecklistItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_order_checklist_order"))
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_checklist_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_order_checklist_source"))
    private ChecklistDefinition sourceChecklist;
    @Column(nullable = false, length = 500, updatable = false)
    private String content;
    @Column(name = "display_order", nullable = false, updatable = false)
    private int displayOrder;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private OrderChecklistSourceType sourceType;

    @Column(name = "evidence_policy_version", nullable = false, updatable = false)
    private int evidencePolicyVersion;
    @Column(name = "minimum_evidence_count", nullable = false, updatable = false)
    private int minimumEvidenceCount;
}
