package com.ondemandmonitoring.zone.dto.response;

import com.ondemandmonitoring.zone.enums.ThermalType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ThermalSourceResponse {

    private String id;
    private String code;
    private String name;
    private String zoneCode;
    private ThermalType thermalType;
    private Double temperatureC;
    private Double centerXM;
    private Double centerYM;
    private Double radiusM;
    private Boolean active;
    private String sourceWorld;
    private String coordinateSystem;
}
