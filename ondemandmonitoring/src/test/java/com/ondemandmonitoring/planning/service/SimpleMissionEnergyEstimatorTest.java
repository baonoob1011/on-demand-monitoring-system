package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ondemandmonitoring.planning.dto.EnergyEstimate;
import com.ondemandmonitoring.planning.dto.EnergyEstimateRequest;
import com.ondemandmonitoring.planning.service.impl.SimpleMissionEnergyEstimator;
import org.junit.jupiter.api.Test;

class SimpleMissionEnergyEstimatorTest {

    @Test
    void coulombCountingCalculatesMahForRepresentedPhases() {
        SimpleMissionEnergyEstimator estimator = estimator();

        EnergyEstimate estimate = estimator.estimate(new EnergyEstimateRequest(20.0, 10.0, 14.0, true, true));

        assertThat(estimate.ascendDurationSec()).isEqualTo(2.0);
        assertThat(estimate.cruiseDurationSec()).isEqualTo(10.0);
        assertThat(estimate.descendDurationSec()).isEqualTo(2.0);
        assertThat(estimate.estimatedEnergyMah()).isCloseTo(
                (6.0 * 1000.0 * 2.0 / 3600.0)
                        + (4.0 * 1000.0 * 10.0 / 3600.0)
                        + (2.0 * 1000.0 * 2.0 / 3600.0),
                org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void cruiseDurationIsDistanceDividedByCruiseSpeed() {
        EnergyEstimate estimate = estimator().estimate(new EnergyEstimateRequest(100.0, 10.0, 10.0, false, false));

        assertThat(estimate.cruiseDurationSec()).isEqualTo(50.0);
        assertThat(estimate.plannedDurationSec()).isEqualTo(50.0);
    }

    @Test
    void verticalClimbUsesCruiseWorldZMinusHomeWorldZ() {
        EnergyEstimate estimate = estimator().estimate(new EnergyEstimateRequest(0.0, 25.0, 55.0, true, false));

        assertThat(estimate.ascendDurationSec()).isEqualTo(15.0);
        assertThat(estimate.plannedDurationSec()).isEqualTo(15.0);
    }

    @Test
    void zeroHorizontalDistanceHasZeroCruiseDuration() {
        EnergyEstimate estimate = estimator().estimate(new EnergyEstimateRequest(0.0, 10.0, 14.0, true, false));

        assertThat(estimate.cruiseDurationSec()).isZero();
        assertThat(estimate.ascendDurationSec()).isEqualTo(2.0);
    }

    @Test
    void equalCruiseAndHomeWorldZHasZeroClimb() {
        EnergyEstimate estimate = estimator().estimate(new EnergyEstimateRequest(10.0, 14.0, 14.0, true, true));

        assertThat(estimate.ascendDurationSec()).isZero();
        assertThat(estimate.descendDurationSec()).isZero();
    }

    @Test
    void batteryPercentAndSafetyReserveAreCalculatedSeparately() {
        SimpleMissionEnergyEstimator estimator = new SimpleMissionEnergyEstimator(
                1000.0,
                0.0,
                3.6,
                0.0,
                1.0,
                1.0,
                1.0,
                20.0);

        EnergyEstimate estimate = estimator.estimate(new EnergyEstimateRequest(100.0, 0.0, 0.0, false, false));

        assertThat(estimate.estimatedEnergyMah()).isEqualTo(100.0);
        assertThat(estimate.estimatedBatteryUsedPercent()).isEqualTo(10.0);
        assertThat(estimate.requiredBatteryPercent()).isEqualTo(30.0);
    }

    @Test
    void sameInputsProduceSameEstimate() {
        SimpleMissionEnergyEstimator estimator = estimator();
        EnergyEstimateRequest request = new EnergyEstimateRequest(80.0, 12.0, 30.0, true, false);

        EnergyEstimate first = estimator.estimate(request);
        EnergyEstimate second = estimator.estimate(request);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void invalidConfigurationFailsClearly() {
        assertThatThrownBy(() -> new SimpleMissionEnergyEstimator(5000.0, 7.5, 5.5, 3.0, 0.0, 1.0, 1.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cruise speed must be positive.");
        assertThatThrownBy(() -> new SimpleMissionEnergyEstimator(5000.0, -1.0, 5.5, 3.0, 2.0, 1.0, 1.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ascend current must be non-negative.");
        assertThatThrownBy(() -> new SimpleMissionEnergyEstimator(0.0, 7.5, 5.5, 3.0, 2.0, 1.0, 1.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Battery capacity must be positive.");
        assertThatThrownBy(() -> new SimpleMissionEnergyEstimator(Double.NaN, 7.5, 5.5, 3.0, 2.0, 1.0, 1.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Battery capacity must be positive.");
    }

    @Test
    void invalidInputsFailClearly() {
        SimpleMissionEnergyEstimator estimator = estimator();

        assertThatThrownBy(() -> estimator.estimate(new EnergyEstimateRequest(Double.POSITIVE_INFINITY, 0.0, 0.0, false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Planned distance must be non-negative.");
        assertThatThrownBy(() -> estimator.estimate(new EnergyEstimateRequest(1.0, Double.NaN, 0.0, false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Home world Z must be finite.");
    }

    private static SimpleMissionEnergyEstimator estimator() {
        return new SimpleMissionEnergyEstimator(
                5000.0,
                6.0,
                4.0,
                2.0,
                2.0,
                2.0,
                2.0,
                15.0);
    }
}
