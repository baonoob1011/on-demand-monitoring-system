package com.ondemandmonitoring.drone.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.drone.enums.DroneOperationalStatus;
import com.ondemandmonitoring.drone.enums.DroneRuntimeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "drone_runtimes")
public class DroneRuntime extends BaseEntity {

    @Column(name = "drone_code", nullable = false, unique = true, length = 50)
    private String droneCode;

    @Column(name = "drone_name", nullable = false, length = 100)
    private String droneName;

    @Enumerated(EnumType.STRING)
    @Column(name = "drone_type", nullable = false)
    private DroneRuntimeType droneType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DroneOperationalStatus status;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
