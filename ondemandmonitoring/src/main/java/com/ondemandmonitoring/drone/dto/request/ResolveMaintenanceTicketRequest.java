package com.ondemandmonitoring.drone.dto.request;

import com.ondemandmonitoring.drone.enums.DroneStatus;
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
     * Target status for the drone after resolution (e.g. AVAILABLE).
     * Defaults to AVAILABLE if null.
     */
    private DroneStatus newDroneStatus;
}
