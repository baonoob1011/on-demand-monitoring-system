package com.ondemandmonitoring.device.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "drones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Drone extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", unique = true)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private DroneModel droneModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payload_id")
    private DronePayload dronePayload;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    public String getDroneCode() {
        return device != null ? device.getSerialNumber() : null;
    }

    public void setDroneCode(String droneCode) {
        ensureDevice().setSerialNumber(droneCode);
    }

    public String getDroneName() {
        return device != null ? device.getName() : null;
    }

    public void setDroneName(String droneName) {
        ensureDevice().setName(droneName);
    }

    public String getSerialNumber() {
        return device != null ? device.getSerialNumber() : null;
    }

    public void setSerialNumber(String serialNumber) {
        ensureDevice().setSerialNumber(serialNumber);
    }

    public DeviceOperationalStatus getStatus() {
        return device != null ? device.getOperationalStatus() : null;
    }

    public void setStatus(DeviceOperationalStatus status) {
        ensureDevice().setOperationalStatus(status);
    }

    private Device ensureDevice() {
        if (device == null) {
            device = new Device();
        }
        return device;
    }
}
