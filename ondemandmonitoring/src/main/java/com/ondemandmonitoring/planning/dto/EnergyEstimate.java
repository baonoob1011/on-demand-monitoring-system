package com.ondemandmonitoring.planning.dto;

public record EnergyEstimate(
        double plannedDurationSec,
        double cruiseDurationSec,
        double ascendDurationSec,
        double descendDurationSec,
        double estimatedEnergyMah,
        double estimatedBatteryUsedPercent,
        double cruiseSpeedMps,
        double batteryCapacityMah,
        double safetyReservePercent,
        double requiredBatteryPercent) {
}
