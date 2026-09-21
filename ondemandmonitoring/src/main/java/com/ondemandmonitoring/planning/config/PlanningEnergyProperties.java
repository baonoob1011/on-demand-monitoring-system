package com.ondemandmonitoring.planning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record PlanningEnergyProperties(
        double batteryCapacityMah,
        double ascendCurrentA,
        double cruiseCurrentA,
        double descendCurrentA,
        double cruiseSpeedMps,
        double ascendSpeedMps,
        double descendSpeedMps,
        double safetyReservePercent) {

    public PlanningEnergyProperties(
            @Value("${planning.energy.battery-capacity-mah:5000.0}") double batteryCapacityMah,
            @Value("${planning.energy.ascend-current-a:7.5}") double ascendCurrentA,
            @Value("${planning.energy.cruise-current-a:5.5}") double cruiseCurrentA,
            @Value("${planning.energy.descend-current-a:3.0}") double descendCurrentA,
            @Value("${planning.energy.cruise-speed-mps:2.0}") double cruiseSpeedMps,
            @Value("${planning.energy.ascend-speed-mps:1.0}") double ascendSpeedMps,
            @Value("${planning.energy.descend-speed-mps:1.0}") double descendSpeedMps,
            @Value("${planning.energy.safety-reserve-percent:20.0}") double safetyReservePercent) {
        this.batteryCapacityMah = requirePositive(batteryCapacityMah, "Battery capacity must be positive.");
        this.ascendCurrentA = requireNonNegative(ascendCurrentA, "Ascend current must be non-negative.");
        this.cruiseCurrentA = requireNonNegative(cruiseCurrentA, "Cruise current must be non-negative.");
        this.descendCurrentA = requireNonNegative(descendCurrentA, "Descend current must be non-negative.");
        this.cruiseSpeedMps = requirePositive(cruiseSpeedMps, "Cruise speed must be positive.");
        this.ascendSpeedMps = requirePositive(ascendSpeedMps, "Ascend speed must be positive.");
        this.descendSpeedMps = requirePositive(descendSpeedMps, "Descend speed must be positive.");
        this.safetyReservePercent = requireNonNegative(safetyReservePercent, "Safety reserve percent must be non-negative.");
    }

    public double cruiseEnergyMah(double distanceM) {
        return cruiseCurrentA * 1000.0 * (distanceM / cruiseSpeedMps) / 3600.0;
    }

    public double ascendEnergyMah(double climbM) {
        return ascendCurrentA * 1000.0 * (climbM / ascendSpeedMps) / 3600.0;
    }

    private static double requirePositive(double value, String message) {
        if (!Double.isFinite(value) || value <= 0.0) throw new IllegalArgumentException(message);
        return value;
    }

    private static double requireNonNegative(double value, String message) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException(message);
        return value;
    }
}
