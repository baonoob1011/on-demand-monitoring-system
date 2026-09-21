package com.ondemandmonitoring.device.dto.response;

import java.time.Instant;
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
public class DeviceModelResponse {

    private String id;
    private String code;
    private String manufacturer;
    private String modelName;
    private Map<String, Object> specsMetadata;
    private Set<DeviceTypeResponse> deviceTypes;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
