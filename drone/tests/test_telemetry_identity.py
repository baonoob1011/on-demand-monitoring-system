import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from telemetry_sender import bound_drone_code


class BoundDroneCodeTest(unittest.TestCase):
    def test_uses_drone_code_from_bound_mission(self):
        self.assertEqual(
            "DRN-0048",
            bound_drone_code({"missionId": "mission-uuid", "deviceCode": "DRN-0048"}),
        )

    def test_does_not_publish_before_mission_binding(self):
        self.assertIsNone(bound_drone_code({"missionId": None, "deviceCode": "DRONE-01"}))
        self.assertIsNone(bound_drone_code({"missionId": "mission-uuid", "deviceCode": None}))

    def test_rejects_invalid_control_status(self):
        self.assertIsNone(bound_drone_code([]))
        self.assertIsNone(bound_drone_code({"missionId": "mission-uuid", "deviceCode": " "}))
        self.assertIsNone(bound_drone_code({"missionId": "mission-uuid", "deviceCode": "x" * 51}))


if __name__ == "__main__":
    unittest.main()
