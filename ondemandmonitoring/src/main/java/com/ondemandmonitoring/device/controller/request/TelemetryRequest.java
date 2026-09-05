package com.ondemandmonitoring.device.controller.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TelemetryRequest {

    @NotNull
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    Double latitude;

    @NotNull
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    Double longitude;

    @NotNull
    Double altitude;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    Double batteryPercent;

    @DecimalMin(value = "0.0")
    Double speed;

    String flightMode;

    @NotNull
    Boolean armed;
}
