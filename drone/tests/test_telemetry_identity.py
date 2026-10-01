import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from telemetry_sender import bound_device_id


class BoundDeviceIdTest(unittest.TestCase):
    def test_uses_device_id_from_bound_mission(self):
        self.assertEqual(
            "device-0048",
            bound_device_id({"missionId": "mission-uuid", "deviceId": "device-0048"}),
        )

    def test_does_not_publish_before_mission_binding(self):
        self.assertIsNone(bound_device_id({"missionId": None, "deviceId": "device-01"}))
        self.assertIsNone(bound_device_id({"missionId": "mission-uuid", "deviceId": None}))

    def test_rejects_invalid_control_status(self):
        self.assertIsNone(bound_device_id([]))
        self.assertIsNone(bound_device_id({"missionId": "mission-uuid", "deviceId": " "}))
        self.assertIsNone(bound_device_id({"missionId": "mission-uuid", "deviceId": "x" * 51}))


if __name__ == "__main__":
    unittest.main()
