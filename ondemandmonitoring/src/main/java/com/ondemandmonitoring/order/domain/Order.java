package com.ondemandmonitoring.order.domain;

import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.order.enums.OrderDeliveryMethod;
import com.ondemandmonitoring.order.enums.OrderPermitStatus;
import com.ondemandmonitoring.order.enums.OrderPriority;
import com.ondemandmonitoring.order.enums.OrderRecurrenceType;
import com.ondemandmonitoring.order.enums.OrderResultFormat;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.order.enums.OrderUsagePurpose;
import com.ondemandmonitoring.order.enums.OrderWeatherFallback;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @Column(name = "order_code", unique = true, length = 40)
    private String orderCode;

    @Column(name = "checklist_snapshot_at")
    private Instant checklistSnapshotAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "service_base_price_snapshot", precision = 19, scale = 0)
    private BigDecimal serviceBasePriceSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_purpose", length = 30)
    private OrderUsagePurpose usagePurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private OrderPriority priority;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "estimated_length_m")
    private Double estimatedLengthM;

    @Column(name = "site_contact_name", length = 120)
    private String siteContactName;

    @Column(name = "site_contact_phone", length = 30)
    private String siteContactPhone;

    @Column(name = "access_notes", columnDefinition = "TEXT")
    private String accessNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "permit_status", length = 20)
    private OrderPermitStatus permitStatus;

    @Column(name = "permit_number", length = 80)
    private String permitNumber;

    @Column(name = "permit_required")
    private Boolean permitRequired;

    @Column(name = "permit_zone_name", length = 120)
    private String permitZoneName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address", columnDefinition = "TEXT")
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
    @Column(name = "recurrence_type", length = 20)
    private OrderRecurrenceType recurrenceType;

    /** Total number of flights when the order repeats (2-52); null when it does not repeat. */
    @Column(name = "recurrence_occurrences")
    private Integer recurrenceOccurrences;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_fallback", length = 30)
    private OrderWeatherFallback weatherFallback;

    /** Latest date by which the customer needs the result. */
    @Column(name = "result_deadline")
    private LocalDate resultDeadline;

    /** Requested result formats (photo, video, PDF report, orthomosaic, 3D model). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_formats", columnDefinition = "jsonb")
    private List<OrderResultFormat> resultFormats;

    /** How the customer receives the result (download, email, API). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_methods", columnDefinition = "jsonb")
    private List<OrderDeliveryMethod> deliveryMethods;

    /** Days the result data is kept after handover. */
    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    /** When the customer accepted the terms and the data-security commitment. */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
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
