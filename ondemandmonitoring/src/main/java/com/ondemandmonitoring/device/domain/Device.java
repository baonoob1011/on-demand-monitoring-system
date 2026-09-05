package com.ondemandmonitoring.device.domain;


import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.enums.DeviceStatus;
import com.ondemandmonitoring.device.enums.DeviceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "devices")
public class Device extends BaseEntity {

    @Column(name = "device_code", nullable = false, unique = true, length = 50)
    private String deviceCode;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeviceStatus status;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}