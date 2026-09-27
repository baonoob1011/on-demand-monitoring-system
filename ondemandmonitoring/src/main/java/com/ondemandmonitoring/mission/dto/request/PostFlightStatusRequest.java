package com.ondemandmonitoring.mission.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.ondemandmonitoring.mission.enums.InspectionResult;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Post-flight status update submitted by an operator after the mission. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostFlightStatusRequest {

    @JsonAlias("newDroneStatus")
    @NotNull(message = "Device status after mission cannot be null")
    DeviceOperationalStatus newDeviceStatus;

    @NotEmpty(message = "Inspection results are required")
    Map<String, InspectionResult> inspectionResults;

    @Size(max = 500)
    String notes;

    TelemetrySnapshot telemetrySnapshot;

    public DeviceOperationalStatus getNewDroneStatus() {
        return newDeviceStatus;
    }

    public void setNewDroneStatus(DeviceOperationalStatus newDroneStatus) {
        this.newDeviceStatus = newDroneStatus;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class TelemetrySnapshot {
        Boolean online;
        String missionId;
        String deviceCode;
        Boolean inAir;
        Boolean positionReady;
        Double altitudeM;
        Double speedMps;
        Double batteryPercent;
        String batteryState;
        Double headingDeg;
    }
}

