package com.ondemandmonitoring.order.domain;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.order.enums.OrderStatus;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

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

    @Column(name = "purpose")
    private String purpose;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private CategoryService service;

    @Column(name = "description")
    private String description;

    @Column(name = "address")
    private String address;

    @Column(name = "point", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point point;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preferred_time_id", nullable = false)
    private PreferredTime preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaTypeSp mediaType;

    @Column(name = "duration_of_video")
    private Integer durationOfVideo;

    @Column(name = "number_of_photo")
    private Integer numberOfPhoto;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;
}
