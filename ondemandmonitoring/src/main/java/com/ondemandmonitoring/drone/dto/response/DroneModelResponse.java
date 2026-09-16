package com.ondemandmonitoring.drone.dto.response;

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
public class DroneModelResponse {

    private String id;
    private String modelCode;
    private String manufacturer;
    private String category;
    private Double maxFlightTimeMinutes;
    private Double maxTakeoffWeightKg;
    private Double maxFlightAltitudeMeters;
    private Double maxWindResistanceMetersPerSecond;
    private String ipRating;
    private String specsMetadata;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
