package com.ondemandmonitoring.order.domain;

import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @Column(name = "title", nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "description")
    private String description;

    @Column(name = "address")
    private String address;

    @Column(name = "point", columnDefinition = "geometry(Point,4326)")
    private Point point;

    @Column(name = "target_area", columnDefinition = "geometry(Polygon,4326)")
    private Polygon targetArea;

    @Column(name = "preferred_date_from")
    private LocalDate preferredDateFrom;

    @Column(name = "preferred_date_to")
    private LocalDate preferredDateTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preferred_time_id", nullable = false)
    private PreferredTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "reject_reason")
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_by")
    private User reviewBy;

    @Column(name = "review_at")
    private Instant reviewAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<CustomerConsultation> consultations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDeliverable> deliverables = new ArrayList<>();
}
