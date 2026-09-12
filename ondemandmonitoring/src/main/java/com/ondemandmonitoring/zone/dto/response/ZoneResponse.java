package com.ondemandmonitoring.zone.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ZoneResponse {

    private String id;
    private String code;
    private String name;
    private String zoneType;
    private String purpose;
    private Double centerXM;
    private Double centerYM;
    private Double radiusM;
    private String sourceWorld;
    private String coordinateSystem;
    private Integer srid;
    private Double areaSquareMeters;
    private Boolean valid;
    private String polygonWkt;
    private List<List<Double>> coordinates;
}
