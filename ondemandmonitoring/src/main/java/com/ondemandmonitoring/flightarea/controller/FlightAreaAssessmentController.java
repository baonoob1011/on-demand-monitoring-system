package com.ondemandmonitoring.flightarea.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.flightarea.dto.FlightAreaAssessmentResponse;
import com.ondemandmonitoring.flightarea.service.FlightAreaAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Flight Area Assessment", description = "Preliminary, advisory assessment of a monitoring area")
@RestController
@RequestMapping("/api/flight-area-assessment")
@RequiredArgsConstructor
public class FlightAreaAssessmentController {

    private final FlightAreaAssessmentService service;

    @Operation(
            summary = "Preliminary flight-area assessment",
            description = "Aggregates terrain elevation, restricted zones (PostGIS) and OpenStreetMap context. "
                    + "Advisory only; the final flight conditions are confirmed during planning and pre-flight. "
                    + "Parameters are plain strings so malformed values return 400, not 500.")
    @PreAuthorize("hasAnyRole('CUSTOMER','STAFF')")
    @GetMapping
    public ApiResponse<FlightAreaAssessmentResponse> assess(
            @RequestParam String latitude,
            @RequestParam String longitude,
            @RequestParam String radiusMeters,
            @RequestParam(required = false) String requestedAltitudeAgl) {
        return ApiResponse.ok(service.assess(latitude, longitude, radiusMeters, requestedAltitudeAgl));
    }
}
