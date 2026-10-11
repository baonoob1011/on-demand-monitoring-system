package com.ondemandmonitoring.finance.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.finance.enums.InvoiceStatus;
import com.ondemandmonitoring.order.domain.Order;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoice_number", columnNames = "invoice_number"),
        @UniqueConstraint(name = "uk_invoice_order", columnNames = "order_id"),
        @UniqueConstraint(name = "uk_invoice_quote", columnNames = "quote_id")})
@Getter @Setter
public class Invoice extends BaseEntity {
    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount;
    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal depositAmount;
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal paidAmount;
    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal remainingAmount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceStatus status;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
}
