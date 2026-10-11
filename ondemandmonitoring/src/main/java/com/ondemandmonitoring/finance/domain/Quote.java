package com.ondemandmonitoring.finance.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.finance.enums.QuoteStatus;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.user.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "quotes", uniqueConstraints = @UniqueConstraint(name = "uk_quote_order_version", columnNames = {"order_id", "quote_version"}),
        indexes = @Index(name = "ix_quotes_order_status", columnList = "order_id,status"))
@Getter @Setter
public class Quote extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "quote_version", nullable = false)
    private int quoteVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuoteStatus status;

    @Column(name = "package_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal packagePrice;
    @Column(name = "additional_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal additionalAmount;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal discountAmount;
    @Column(name = "adjustment_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal adjustmentAmount;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount;
    @Column(name = "manager_note", length = 2000)
    private String managerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<QuoteItem> items = new ArrayList<>();

    public void replaceItems(List<QuoteItem> replacements) {
        items.clear();
        replacements.forEach(item -> { item.setQuote(this); items.add(item); });
    }
}
