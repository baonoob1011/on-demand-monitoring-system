package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.config.PlanningEnergyProperties;
import com.ondemandmonitoring.planning.service.MissionEnergyEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SimpleMissionEnergyEstimator implements MissionEnergyEstimator {

    private final double batteryCapacityMah;
    private final double ascendCurrentA;
    private final double cruiseCurrentA;
    private final double descendCurrentA;
    private final double cruiseSpeedMps;
    private final double ascendSpeedMps;
    private final double descendSpeedMps;
    private final double safetyReservePercent;

    @Autowired
    public SimpleMissionEnergyEstimator(PlanningEnergyProperties properties) {
        this(properties.batteryCapacityMah(), properties.ascendCurrentA(), properties.cruiseCurrentA(),
                properties.descendCurrentA(), properties.cruiseSpeedMps(), properties.ascendSpeedMps(),
                properties.descendSpeedMps(), properties.safetyReservePercent());
    }

    public SimpleMissionEnergyEstimator(
            double batteryCapacityMah, double ascendCurrentA, double cruiseCurrentA, double descendCurrentA,
            double cruiseSpeedMps, double ascendSpeedMps, double descendSpeedMps, double safetyReservePercent) {
        this.batteryCapacityMah = batteryCapacityMah;
        this.ascendCurrentA = ascendCurrentA;
        this.cruiseCurrentA = cruiseCurrentA;
        this.descendCurrentA = descendCurrentA;
        this.cruiseSpeedMps = cruiseSpeedMps;
        this.ascendSpeedMps = ascendSpeedMps;
        this.descendSpeedMps = descendSpeedMps;
        this.safetyReservePercent = safetyReservePercent;
        validateConfiguration();
    }

    @Override
    public EnergyEstimate estimate(EnergyEstimateRequest request) {
        validateInput(request);

        double climbM = Math.max(0.0, request.plannedCruiseWorldZ() - request.homeWorldZ());
        double ascendDurationSec = request.includeAscend() ? climbM / ascendSpeedMps : 0.0;
        double cruiseDurationSec = request.plannedDistanceM() / cruiseSpeedMps;
        double descendDurationSec = request.includeDescend() ? climbM / descendSpeedMps : 0.0;

        double ascendEnergyMah = mah(ascendCurrentA, ascendDurationSec);
        double cruiseEnergyMah = mah(cruiseCurrentA, cruiseDurationSec);
        double descendEnergyMah = mah(descendCurrentA, descendDurationSec);
        double estimatedEnergyMah = ascendEnergyMah + cruiseEnergyMah + descendEnergyMah;
        double estimatedBatteryUsedPercent = estimatedEnergyMah / batteryCapacityMah * 100.0;
        double requiredBatteryPercent = estimatedBatteryUsedPercent + safetyReservePercent;
        double plannedDurationSec = ascendDurationSec + cruiseDurationSec + descendDurationSec;

        return new EnergyEstimate(
                plannedDurationSec,
                cruiseDurationSec,
                ascendDurationSec,
                descendDurationSec,
                estimatedEnergyMah,
                estimatedBatteryUsedPercent,
                cruiseSpeedMps,
                batteryCapacityMah,
                safetyReservePercent,
                requiredBatteryPercent);
    }

    private double mah(double currentA, double durationSec) {
        return currentA * 1000.0 * durationSec / 3600.0;
    }

    private void validateConfiguration() {
        requirePositive(batteryCapacityMah, "Battery capacity must be positive.");
        requireNonNegative(ascendCurrentA, "Ascend current must be non-negative.");
        requireNonNegative(cruiseCurrentA, "Cruise current must be non-negative.");
        requireNonNegative(descendCurrentA, "Descend current must be non-negative.");
        requirePositive(cruiseSpeedMps, "Cruise speed must be positive.");
        requirePositive(ascendSpeedMps, "Ascend speed must be positive.");
        requirePositive(descendSpeedMps, "Descend speed must be positive.");
        requireNonNegative(safetyReservePercent, "Safety reserve percent must be non-negative.");
    }

    private void validateInput(EnergyEstimateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Energy estimate request is required.");
        }
        requireNonNegative(request.plannedDistanceM(), "Planned distance must be non-negative.");
        requireFinite(request.homeWorldZ(), "Home world Z must be finite.");
        requireFinite(request.plannedCruiseWorldZ(), "Planned cruise world Z must be finite.");
    }

    private void requirePositive(double value, String message) {
        requireFinite(value, message);
        if (value <= 0.0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireNonNegative(double value, String message) {
        requireFinite(value, message);
        if (value < 0.0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireFinite(double value, String message) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
