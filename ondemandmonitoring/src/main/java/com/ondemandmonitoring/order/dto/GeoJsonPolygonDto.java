package com.ondemandmonitoring.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "GeoJSON Polygon location format")
public class GeoJsonPolygonDto {

    @Builder.Default
    @Schema(description = "GeoJSON Geometry type", example = "Polygon")
    private String type = "Polygon";

    @NotEmpty(message = "Coordinates must not be empty")
    @Schema(description = "Coordinates array of linear rings: [[[longitude, latitude], ...]]",
            example = "[[[106.66, 10.76], [106.67, 10.76], [106.67, 10.77], [106.66, 10.77], [106.66, 10.76]]]")
    private List<List<List<Double>>> coordinates;

    public static GeoJsonPolygonDto of(List<List<List<Double>>> coordinates) {
        return GeoJsonPolygonDto.builder()
                .type("Polygon")
                .coordinates(coordinates)
                .build();
    }
}
