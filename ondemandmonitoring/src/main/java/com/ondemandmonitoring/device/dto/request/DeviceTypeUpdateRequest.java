package com.ondemandmonitoring.device.dto.request;

import jakarta.validation.constraints.Size;
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
public class DeviceTypeUpdateRequest {

    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String description;
}
