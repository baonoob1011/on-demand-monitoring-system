package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.drone.enums.PreflightCheckLevel;
import com.ondemandmonitoring.drone.enums.PreflightItemStatus;
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
        name = "preflight_check_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_preflight_item_type",
                columnNames = {"preflight_check_id", "check_type"}),
        indexes = @Index(
                name = "idx_preflight_item_run",
                columnList = "preflight_check_id"))
public class PersistedPreflightCheckItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preflight_check_id", nullable = false)
    PersistedPreflightCheck preflightCheck;

    @Column(name = "check_type", nullable = false, length = 50)
    String checkType;

    @Column(name = "check_name", nullable = false, length = 120)
    String checkName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PreflightItemStatus status = PreflightItemStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_level", nullable = false, length = 20)
    PreflightCheckLevel checkLevel;

    @Column(length = 1000)
    String message;

    @Column(name = "checked_at")
    Instant checkedAt;
}
