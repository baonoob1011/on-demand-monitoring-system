package com.ondemandmonitoring.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    @NotBlank(message = "Service ID is required")
    @Schema(description = "Service ID", example = "550e8400-e29b-41d4-a716-446655440000")
    String serviceId;

    @Schema(description = "Detailed description of the monitoring request")
    String description;

    @Schema(description = "Address of the monitoring target location")
    String address;

    @NotNull(message = "Longitude is required")
    @Schema(description = "Longitude coordinate", example = "106.660172")
    Double longitude;

    @NotNull(message = "Latitude is required")
    @Schema(description = "Latitude coordinate", example = "10.762622")
    Double latitude;

    @NotNull(message = "Coverage area GeoJSON map is required")
    @Schema(description = "Coverage area GeoJSON object map")
    Map<String, Object> coverageArea;

    @NotNull(message = "Preferred date from is required")
    @Schema(description = "Preferred monitoring start date", example = "2026-10-01")
    LocalDate preferredDateFrom;

    @NotNull(message = "Preferred date to is required")
    @Schema(description = "Preferred monitoring end date", example = "2026-10-05")
    LocalDate preferredDateTo;

    @NotBlank(message = "Preferred time ID is required")
    @Schema(description = "Preferred time frame ID")
    String preferredTimeId;

    @NotEmpty(message = "At least one deliverable is required")
    @Valid
    @Schema(description = "List of deliverable types with requirements for the service")
    List<OrderDeliverableRequest> deliverables;
}
