package com.ondemandmonitoring.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
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
@Schema(description = "GeoJSON Point location format")
public class GeoJsonPointDto {

    @Builder.Default
    @Schema(description = "GeoJSON Geometry type", example = "Point")
    private String type = "Point";

    @NotEmpty(message = "Coordinates must not be empty")
    @Size(min = 2, max = 2, message = "Coordinates must contain [longitude, latitude]")
    @Schema(description = "Location coordinates array: [longitude, latitude]", example = "[106.660172, 10.762622]")
    private List<Double> coordinates;

    public Double getLongitude() {
        return (coordinates != null && coordinates.size() >= 1) ? coordinates.get(0) : null;
    }

    public Double getLatitude() {
        return (coordinates != null && coordinates.size() >= 2) ? coordinates.get(1) : null;
    }

    public static GeoJsonPointDto of(double longitude, double latitude) {
        return GeoJsonPointDto.builder()
                .type("Point")
                .coordinates(List.of(longitude, latitude))
                .build();
    }
}
