package com.ondemandmonitoring.order.dto.request;

import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request object for creating a monitoring order")
public class OrderCreateRequest {

    @NotBlank(message = "Title is required")
    @Schema(description = "Title of the order", example = "Forest Area Monitoring")
    String title;

    @Schema(description = "Purpose of the monitoring order", example = "Wildfire risk detection")
    String purpose;

    @NotNull(message = "Service ID is required")
    @Schema(description = "Category Service ID", example = "1")
    Long serviceId;

    @Schema(description = "Detailed description of the monitoring request")
    String description;

    @Schema(description = "Address of the monitoring target location")
    String address;

    @Valid
    @NotNull(message = "Point location is required")
    @Schema(description = "GeoJSON Point location format")
    GeoJsonPointDto point;

    @NotNull(message = "Preferred date is required")
    @Schema(description = "Preferred monitoring date", example = "2026-10-01")
    LocalDate preferredDate;

    @NotBlank(message = "Preferred time ID is required")
    @Schema(description = "Preferred time frame ID")
    String preferredTimeId;

    @NotNull(message = "Media type is required")
    @Schema(description = "Media output type: IMAGE or VIDEO")
    MediaTypeSp mediaType;

    @Schema(description = "Duration of video in seconds (Required if mediaType is VIDEO)", example = "120")
    Integer durationOfVideo;

    @Schema(description = "Number of photos (Required if mediaType is IMAGE)", example = "10")
    Integer numberOfPhoto;
}
