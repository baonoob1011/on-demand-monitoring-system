package com.ondemandmonitoring.drone.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
public class DroneModelUpdateRequest {

    @NotBlank(message = "Model code is required")
    private String modelCode;

    @NotBlank(message = "Manufacturer is required")
    private String manufacturer;

    @NotBlank(message = "Category is required")
    private String category;

    @Positive(message = "Max flight time must be positive")
    private Double maxFlightTimeMinutes;

    @Positive(message = "Max takeoff weight must be positive")
    private Double maxTakeoffWeightKg;

    @Positive(message = "Max flight altitude must be positive")
    private Double maxFlightAltitudeMeters;

    @Positive(message = "Max wind resistance must be positive")
    private Double maxWindResistanceMetersPerSecond;

    private String ipRating;

    private String specsMetadata;
}
