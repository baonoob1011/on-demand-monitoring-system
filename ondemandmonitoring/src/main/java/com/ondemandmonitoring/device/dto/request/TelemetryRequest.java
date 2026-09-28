package com.ondemandmonitoring.device.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TelemetryRequest {
    Double simX;
    Double simY;
    Double altitude;
    Double relativeAltitude;
}
