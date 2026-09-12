package com.ondemandmonitoring.zone.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZonePolygonUpdateRequest {

    @NotEmpty
    private List<List<Double>> coordinates;
}
