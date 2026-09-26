package com.ondemandmonitoring.missionv2.dto.request;

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
public class AssignStaffRequest {
    @NotBlank(message = "staffId is required")
    private String staffId;

    private String assignedRole;
}
