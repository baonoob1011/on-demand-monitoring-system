package com.ondemandmonitoring.device.dto.request;

import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
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
public class DeviceModelUpdateRequest {

    @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;

    @Size(max = 100, message = "Manufacturer must not exceed 100 characters")
    private String manufacturer;

    @Size(max = 100, message = "Model name must not exceed 100 characters")
    private String modelName;

    private Map<String, Object> specsMetadata;

    private Set<String> deviceTypeIds;
}
