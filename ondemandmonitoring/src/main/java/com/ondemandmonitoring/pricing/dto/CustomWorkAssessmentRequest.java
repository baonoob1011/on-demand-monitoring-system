package com.ondemandmonitoring.pricing.dto;

import com.ondemandmonitoring.pricing.enums.TerrainType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CustomWorkAssessmentRequest(@DecimalMin("0") BigDecimal estimatedFlightHours,
        @DecimalMin("0") BigDecimal estimatedDistanceKm, @DecimalMin("0") BigDecimal estimatedAreaHa,
        @Min(1) Integer pilotCount, @Min(1) Integer droneCount, @Min(0) Integer batteryUnits,
        @NotNull TerrainType terrain, boolean thermalRequired, boolean multispectralRequired,
        boolean nightOperation, @Size(max = 500) String specialEquipment,
        @Size(max = 1000) String otherCostDrivers, boolean assessmentComplete) {}
