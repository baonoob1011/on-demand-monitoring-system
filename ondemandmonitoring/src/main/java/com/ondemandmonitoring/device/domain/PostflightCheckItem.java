package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.mission.enums.InspectionResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "postflight_check_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_postflight_check_item_type",
                columnNames = {"postflight_check_id", "check_type"}),
        indexes = @Index(
                name = "idx_postflight_check_item_check",
                columnList = "postflight_check_id"))
public class PostflightCheckItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "postflight_check_id", nullable = false)
    PostflightCheck postflightCheck;

    @Column(name = "check_type", nullable = false, length = 50)
    String checkType;

    @Column(name = "check_name", nullable = false, length = 120)
    String checkName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InspectionResult status;

    @Column(length = 1000)
    String message;

    @Column(name = "checked_at")
    Instant checkedAt;
}
