import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from battery_simulator import (  # noqa: E402
    ASCEND_CURRENT_A,
    CRUISE_CURRENT_A,
    HOVER_CURRENT_A,
    LANDED_CURRENT_A,
    MODE_STABILIZATION_SECONDS,
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
    def test_starts_at_one_hundred_percent_by_default(self) -> None:
        simulator = BatterySimulator(clock=FakeClock())

        self.assertAlmostEqual(100.0, simulator.percent)
        self.assertAlmostEqual(5000.0, simulator.get_remaining_mah())
        self.assertAlmostEqual(0.0, simulator.get_consumed_mah())

    def test_starts_at_configured_initial_percentage(self) -> None:
        simulator = BatterySimulator(80.0, capacity_mAh=5000.0, clock=FakeClock())

        self.assertAlmostEqual(80.0, simulator.percent)
        self.assertAlmostEqual(4000.0, simulator.get_remaining_mah())
        self.assertAlmostEqual(1000.0, simulator.get_consumed_mah())

    def test_clamps_initial_percentage_between_zero_and_one_hundred(self) -> None:
        self.assertEqual(100.0, BatterySimulator(120.0, clock=FakeClock()).percent)
        self.assertEqual(0.0, BatterySimulator(-10.0, clock=FakeClock()).percent)

    def test_hover_at_4a_for_one_minute_consumes_66_67mah(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(60.0)

        snapshot = simulator.update("HOVER")

        self.assertAlmostEqual(66.6667, snapshot.consumed_mAh, places=3)
        self.assertAlmostEqual(4933.3333, snapshot.remaining_mAh, places=3)
        self.assertAlmostEqual(98.6667, snapshot.battery_percent, places=3)

    def test_hover_at_4a_for_ten_minutes_leaves_86_67_percent(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(600.0)

        snapshot = simulator.update("HOVER")

        self.assertAlmostEqual(666.6667, snapshot.consumed_mAh, places=3)
        self.assertAlmostEqual(86.6667, snapshot.battery_percent, places=3)

    def test_cruise_at_5_5a_for_ten_minutes_leaves_81_67_percent(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(600.0)

        snapshot = simulator.update("CRUISE")

        self.assertAlmostEqual(916.6667, snapshot.consumed_mAh, places=3)
        self.assertAlmostEqual(81.6667, snapshot.battery_percent, places=3)

    def test_ascend_at_7_5a_for_ten_minutes_leaves_75_percent(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(600.0)

        snapshot = simulator.update("ASCEND")

        self.assertAlmostEqual(1250.0, snapshot.consumed_mAh, places=3)
        self.assertAlmostEqual(75.0, snapshot.battery_percent, places=3)

    def test_ascend_drains_faster_than_hover_for_same_duration(self) -> None:
        ascend_clock = FakeClock()
        hover_clock = FakeClock()
        ascend = BatterySimulator(100.0, capacity_mAh=5000.0, clock=ascend_clock)
        hover = BatterySimulator(100.0, capacity_mAh=5000.0, clock=hover_clock)
        ascend_clock.advance(60.0)
        hover_clock.advance(60.0)

        ascend.update("ASCEND")
        hover.update("HOVER")

        self.assertGreater(ascend.get_consumed_mah(), hover.get_consumed_mah())
        self.assertGreater(ASCEND_CURRENT_A, HOVER_CURRENT_A)

    def test_hover_drains_slower_than_cruise_for_same_duration(self) -> None:
        cruise_clock = FakeClock()
        hover_clock = FakeClock()
        cruise = BatterySimulator(100.0, capacity_mAh=5000.0, clock=cruise_clock)
        hover = BatterySimulator(100.0, capacity_mAh=5000.0, clock=hover_clock)
        cruise_clock.advance(60.0)
        hover_clock.advance(60.0)

        cruise.update("CRUISE")
        hover.update("HOVER")

        self.assertGreater(cruise.get_consumed_mah(), hover.get_consumed_mah())
        self.assertGreater(CRUISE_CURRENT_A, HOVER_CURRENT_A)

    def test_disarmed_landed_drains_much_slower_than_hover(self) -> None:
        landed_clock = FakeClock()
        hover_clock = FakeClock()
        landed = BatterySimulator(100.0, capacity_mAh=5000.0, clock=landed_clock)
        hover = BatterySimulator(100.0, capacity_mAh=5000.0, clock=hover_clock)
        landed_clock.advance(60.0)
        hover_clock.advance(60.0)

        landed.update("LANDED")
        hover.update("HOVER")

        self.assertLess(landed.get_consumed_mah(), hover.get_consumed_mah())
        self.assertLess(LANDED_CURRENT_A, HOVER_CURRENT_A)

    def test_battery_never_exceeds_one_hundred_percent(self) -> None:
        simulator = BatterySimulator(120.0, capacity_mAh=5000.0, clock=FakeClock())

        self.assertEqual(100.0, simulator.percent)

    def test_battery_never_goes_below_zero_percent(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(1.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(60 * 60)

        simulator.update("ASCEND")

        self.assertEqual(0.0, simulator.percent)
        self.assertEqual(0.0, simulator.get_remaining_mah())

    def test_repeated_api_reads_do_not_drain_battery_by_themselves(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, clock=clock)

        first = simulator.snapshot().battery_percent
        second = simulator.snapshot().battery_percent

        self.assertEqual(first, second)

    def test_drain_depends_on_elapsed_time_not_poll_frequency(self) -> None:
        single_clock = FakeClock()
        frequent_clock = FakeClock()
        single = BatterySimulator(100.0, capacity_mAh=5000.0, clock=single_clock)
        frequent = BatterySimulator(100.0, capacity_mAh=5000.0, clock=frequent_clock)

        single_clock.advance(60.0)
        single.update("HOVER")

        for _ in range(60):
            frequent_clock.advance(1.0)
            frequent.update("HOVER")

        self.assertAlmostEqual(single.percent, frequent.percent, places=6)
        self.assertAlmostEqual(single.get_consumed_mah(), frequent.get_consumed_mah(), places=6)

    def test_mode_detection_respects_armed_idle(self) -> None:
        self.assertEqual("LANDED", detect_battery_mode(armed=False, in_air=False))
        self.assertEqual("ARMED_IDLE", detect_battery_mode(armed=True, in_air=False))

    def test_climb_and_descent_mode_detection_respects_px4_ned_sign(self) -> None:
        self.assertEqual("HOVER", detect_battery_mode(armed=True, in_air=True, velocity_down_m_s=-0.1))
        self.assertEqual("HOVER", detect_battery_mode(armed=True, in_air=True, velocity_down_m_s=-0.3))
        self.assertEqual("ASCEND", detect_battery_mode(armed=True, in_air=True, velocity_down_m_s=-0.6))
        self.assertEqual("DESCEND", detect_battery_mode(armed=True, in_air=True, velocity_down_m_s=0.6))

    def test_horizontal_motion_uses_cruise_mode(self) -> None:
        self.assertEqual("HOVER", detect_battery_mode(armed=True, in_air=True, velocity_north_m_s=0.3))
        self.assertEqual("CRUISE", detect_battery_mode(armed=True, in_air=True, velocity_north_m_s=0.6))

    def test_auto_ascend_requires_stabilization_window(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)

        simulator.update(
            armed=True,
            in_air=True,
            velocity_down_m_s=-0.6,
        )
        self.assertEqual("LANDED", simulator.mode)

        clock.advance(MODE_STABILIZATION_SECONDS + 0.1)
        simulator.update(
            armed=True,
            in_air=True,
            velocity_down_m_s=-0.6,
        )

        self.assertEqual("ASCEND", simulator.mode)

    def test_stationary_in_air_returns_to_hover(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        simulator.update("ASCEND")
        clock.advance(1.0)

        simulator.update(armed=True, in_air=True)

        self.assertEqual("HOVER", simulator.mode)

    def test_repeated_preflight_reads_do_not_drain_battery(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, clock=clock)

        first = preflight_battery_check(simulator.snapshot().battery_percent)
        second = preflight_battery_check(simulator.snapshot().battery_percent)

        self.assertEqual(first, second)
        self.assertAlmostEqual(100.0, simulator.percent)

    def test_one_elapsed_interval_is_never_integrated_twice(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(60.0)

        first = simulator.update("HOVER")
        second = simulator.update("HOVER")

        self.assertAlmostEqual(first.consumed_mAh, second.consumed_mAh, places=6)

    def test_frontend_refresh_snapshot_does_not_reset_or_drain_battery(self) -> None:
        clock = FakeClock()
        simulator = BatterySimulator(100.0, capacity_mAh=5000.0, clock=clock)
        clock.advance(60.0)
        drained = simulator.update("HOVER").battery_percent

        for _ in range(10):
            self.assertAlmostEqual(drained, simulator.snapshot().battery_percent)

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

    def test_preflight_battery_status_thresholds(self) -> None:
        self.assertEqual("PASS", preflight_battery_check(87.4)[0])
        self.assertEqual("WARN", preflight_battery_check(25.0)[0])
        self.assertEqual("FAIL", preflight_battery_check(20.0)[0])


if __name__ == "__main__":
    unittest.main()
