import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from visualization.thermal_debug_viewer import (  # noqa: E402
    ThermalStatus,
    draw_thermal_debug,
    parse_status,
)


class ThermalDebugViewerTest(unittest.TestCase):
    def test_starts_with_no_frame_and_off_state(self) -> None:
        status = ThermalStatus(enabled=False)

        image = draw_thermal_debug(None, status, 0.0, "/thermal")

        self.assertEqual((520, 900, 3), image.shape)

    def test_off_state_has_no_fake_measurement(self) -> None:
        status = parse_status({"thermalEnabled": False, "maxTemperatureC": None, "hotspotDetected": False})

        self.assertFalse(status.enabled)
        self.assertIsNone(status.max_temp_c)
        self.assertFalse(status.hotspot_detected)

    def test_valid_status_computes_display_values(self) -> None:
        status = parse_status({
            "online": True,
            "thermalEnabled": True,
            "thermalSensorOnline": True,
            "thermalFrameAgeMs": 120,
            "maxTemperatureC": 82.5,
            "averageTemperatureC": 34.2,
            "hotspotDetected": True,
            "hotspotTemperatureC": 82.5,
        })

        self.assertTrue(status.enabled)
        self.assertTrue(status.sensor_online)
        self.assertEqual(82.5, status.max_temp_c)
        self.assertEqual(34.2, status.avg_temp_c)
        self.assertTrue(status.hotspot_detected)

    def test_invalid_status_does_not_crash_viewer(self) -> None:
        status = parse_status(None)

        image = draw_thermal_debug(None, status, 0.0, "/thermal")

        self.assertEqual((520, 900, 3), image.shape)

    def test_hotspot_threshold_is_reflected_from_status(self) -> None:
        cold = parse_status({"thermalEnabled": True, "thermalSensorOnline": True, "maxTemperatureC": 41.0, "hotspotDetected": False})
        hot = parse_status({"thermalEnabled": True, "thermalSensorOnline": True, "maxTemperatureC": 110.0, "hotspotDetected": True})

        self.assertFalse(cold.hotspot_detected)
        self.assertTrue(hot.hotspot_detected)

    def test_visualization_does_not_modify_frame(self) -> None:
        frame = np.full((32, 48, 3), 80, dtype=np.uint8)
        original = frame.copy()
        status = parse_status({"thermalEnabled": True, "thermalSensorOnline": True, "maxTemperatureC": 65.0, "hotspotDetected": True})

        draw_thermal_debug(frame, status, 6.0, "/thermal")

        np.testing.assert_array_equal(original, frame)

    def test_native_visualization_controls_are_reflected_from_status(self) -> None:
        status = parse_status({
            "thermalMode": "NATIVE_GAZEBO",
            "thermalPixelFormat": "L_INT16",
            "thermalPalette": "BLACK_HOT",
            "thermalIsothermEnabled": True,
            "thermalDebugOverlayEnabled": True,
            "thermalDisplayRangeMode": "AUTO",
        })

        self.assertEqual("NATIVE_GAZEBO", status.mode)
        self.assertEqual("L_INT16", status.pixel_format)
        self.assertEqual("BLACK_HOT", status.palette)
        self.assertTrue(status.isotherm_enabled)
        self.assertTrue(status.debug_overlay_enabled)
        self.assertEqual("AUTO", status.display_range_mode)


if __name__ == "__main__":
    unittest.main()
