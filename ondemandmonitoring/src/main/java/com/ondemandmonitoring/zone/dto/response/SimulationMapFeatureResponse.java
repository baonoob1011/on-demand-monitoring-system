package com.ondemandmonitoring.zone.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimulationMapFeatureResponse {

    private String id;
    private String code;
    private String name;
    private String featureType;
    private Integer displayOrder;
    private String sourceWorld;
    private String coordinateSystem;
    private String geometryType;
    private Integer srid;
    private Double areaSquareMeters;
    private Double lengthMeters;
    private Boolean valid;
    private String geometryWkt;
    private List<List<Double>> coordinates;
}
