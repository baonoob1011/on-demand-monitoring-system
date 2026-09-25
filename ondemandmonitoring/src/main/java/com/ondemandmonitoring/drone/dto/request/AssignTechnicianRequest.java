package com.ondemandmonitoring.drone.dto.request;

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
public class AssignTechnicianRequest {

    @NotBlank(message = "Technician ID must not be blank")
    private String technicianId;
}
