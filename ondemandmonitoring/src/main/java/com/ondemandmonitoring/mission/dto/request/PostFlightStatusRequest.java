package com.ondemandmonitoring.mission.dto.request;

import com.ondemandmonitoring.drone.enums.DroneStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import com.ondemandmonitoring.mission.enums.InspectionResult;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Post-flight status update submitted by Drone Operator after the mission. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostFlightStatusRequest {

    @NotNull(message = "Drone status after flight cannot be null")
    DroneStatus newDroneStatus;

    @NotEmpty(message = "Inspection results are required")
    Map<String, InspectionResult> inspectionResults;

    @Size(max = 500)
    String notes;

    TelemetrySnapshot telemetrySnapshot;

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

