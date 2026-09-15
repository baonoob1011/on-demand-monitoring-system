import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from battery_simulator import (
    ASCEND_DRAIN_PERCENT_PER_MIN,
    CRUISE_DRAIN_PERCENT_PER_MIN,
    HOVER_DRAIN_PERCENT_PER_MIN,
    LANDED_DRAIN_PERCENT_PER_MIN,
    BatterySimulator,
    battery_state,
    detect_battery_mode,
    preflight_battery_check,
)


class FakeClock:
    def __init__(self) -> None:
        self.now_s = 0.0

    def __call__(self) -> float:
        return self.now_s

    def advance(self, seconds: float) -> None:
        self.now_s += seconds


class BatterySimulatorTest(unittest.TestCase):
    def test_starts_at_configured_initial_percentage(self) -> None:
        simulator = BatterySimulator(87.4, clock=FakeClock())
        self.assertAlmostEqual(87.4, simulator.percent)

    def test_clamps_initial_percentage_between_zero_and_one_hundred(self) -> None:
        self.assertEqual(100.0, BatterySimulator(120.0, clock=FakeClock()).percent)
        self.assertEqual(0.0, BatterySimulator(-10.0, clock=FakeClock()).percent)

    def test_delta_time_calculation_uses_elapsed_minutes(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, clock=clock)
        clock.advance(30.0)

        simulator.update("CRUISE")

        self.assertAlmostEqual(100.0 - CRUISE_DRAIN_PERCENT_PER_MIN * 0.5, simulator.percent)

    def test_repeated_api_reads_do_not_drain_battery_by_themselves(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, clock=clock)

        first = simulator.snapshot().battery_percent
        second = simulator.snapshot().battery_percent

        self.assertEqual(first, second)

    def test_landed_drain_is_slower_than_hover(self) -> None:
        self.assertLess(LANDED_DRAIN_PERCENT_PER_MIN, HOVER_DRAIN_PERCENT_PER_MIN)

    def test_hover_drain_is_slower_than_cruise_and_ascend(self) -> None:
        self.assertLess(HOVER_DRAIN_PERCENT_PER_MIN, CRUISE_DRAIN_PERCENT_PER_MIN)
        self.assertLess(HOVER_DRAIN_PERCENT_PER_MIN, ASCEND_DRAIN_PERCENT_PER_MIN)

    def test_battery_state_thresholds(self) -> None:
        cases = [
            (31.0, "NORMAL"),
            (30.0, "LOW"),
            (21.0, "LOW"),
            (20.0, "CRITICAL"),
            (11.0, "CRITICAL"),
            (10.0, "EMERGENCY"),
        ]
        for percent, expected in cases:
            with self.subTest(percent=percent):
                self.assertEqual(expected, battery_state(percent))

    def test_climb_and_descent_mode_detection_respects_px4_ned_sign(self) -> None:
        self.assertEqual("ASCEND", detect_battery_mode(in_air=True, velocity_down_m_s=-0.3))
        self.assertEqual("DESCEND", detect_battery_mode(in_air=True, velocity_down_m_s=0.3))

    def test_horizontal_motion_uses_cruise_mode(self) -> None:
        self.assertEqual("CRUISE", detect_battery_mode(in_air=True, velocity_north_m_s=0.4))

    def test_preflight_battery_status_thresholds(self) -> None:
        self.assertEqual("PASS", preflight_battery_check(87.4)[0])
        self.assertEqual("WARN", preflight_battery_check(25.0)[0])
        self.assertEqual("FAIL", preflight_battery_check(20.0)[0])


if __name__ == "__main__":
    unittest.main()
