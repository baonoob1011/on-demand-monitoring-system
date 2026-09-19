package com.ondemandmonitoring.zone.dto.request;

import com.ondemandmonitoring.zone.enums.ThermalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ThermalSourceRequest {

    private String code;

    @NotBlank
    private String name;

    @NotBlank
    private String zoneCode;

    @NotNull
    private ThermalType thermalType;

    @NotNull
    private Double temperatureC;

    @NotNull
    private Double centerXM;

    @NotNull
    private Double centerYM;

    @NotNull
    @Positive
    private Double radiusM;

    private Boolean active;
}
