package com.ondemandmonitoring.device.dto.response;

import java.time.Instant;
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
public class DeviceTypeResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
