package com.ondemandmonitoring.zone.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneCreateRequest {

    @NotBlank
    private String name;

    private String code;

    private String zoneType;

    private String purpose;

    @NotEmpty
    private List<List<Double>> coordinates;
}
