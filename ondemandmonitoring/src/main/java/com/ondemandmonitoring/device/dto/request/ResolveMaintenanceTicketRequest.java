package com.ondemandmonitoring.device.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ondemandmonitoring.device.enums.DeviceOperationalStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveMaintenanceTicketRequest {

    @NotBlank(message = "Resolution notes must not be blank")
    private String resolutionNotes;

    /**
     * Target operational status for the device after resolution (e.g. AVAILABLE).
     * Defaults to AVAILABLE if null.
     */
    @JsonAlias("newDroneStatus")
    private DeviceOperationalStatus newDeviceStatus;

    public DeviceOperationalStatus getNewDroneStatus() {
        return newDeviceStatus;
    }

    public void setNewDroneStatus(DeviceOperationalStatus newDroneStatus) {
        this.newDeviceStatus = newDroneStatus;
    }
}
