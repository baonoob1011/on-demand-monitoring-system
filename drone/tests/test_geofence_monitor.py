import asyncio
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from geofence_monitor import (
    GeofenceLevel,
    RestrictedZone,
    RestrictedZoneCache,
    evaluate_geofence,
    parse_restricted_zones,
    px4_ned_to_sim_xy,
)


def zone(
    zone_id: str,
    code: str,
    name: str,
    coords: list[tuple[float, float]],
    zone_type: str = "AIRPORT",
) -> RestrictedZone:
    ring = coords + [coords[0]] if coords[0] != coords[-1] else coords
    return RestrictedZone(
        id=zone_id,
        code=code,
        name=name,
        zone_type=zone_type,
        coordinates=tuple(ring),
    )


class GeofenceMonitorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.square = zone(
            "a",
            "RESTRICTED_A",
            "Restricted A",
            [(0, 0), (100, 0), (100, 100), (0, 100)],
        )

    def evaluate(self, point: tuple[float, float], zones: list[RestrictedZone] | None = None):
        return evaluate_geofence(
            point,
            zones if zones is not None else [self.square],
            caution_distance_m=50,
            danger_distance_m=20,
        )

    def test_point_far_away_is_safe(self) -> None:
        state = self.evaluate((200, 200))
        self.assertEqual(GeofenceLevel.SAFE, state.level)
        self.assertAlmostEqual(141.421, state.distance_m or 0, places=3)

    def test_point_40m_from_polygon_is_caution(self) -> None:
        state = self.evaluate((140, 50))
        self.assertEqual(GeofenceLevel.CAUTION, state.level)
        self.assertAlmostEqual(40.0, state.distance_m or 0)

    def test_point_10m_from_polygon_is_danger(self) -> None:
        state = self.evaluate((110, 50))
        self.assertEqual(GeofenceLevel.DANGER, state.level)
        self.assertAlmostEqual(10.0, state.distance_m or 0)

    def test_point_on_polygon_boundary_is_violation(self) -> None:
        state = self.evaluate((100, 50))
        self.assertEqual(GeofenceLevel.VIOLATION, state.level)
        self.assertTrue(state.inside)

    def test_point_inside_polygon_is_violation(self) -> None:
        state = self.evaluate((50, 50))
        self.assertEqual(GeofenceLevel.VIOLATION, state.level)
        self.assertTrue(state.inside)

    def test_irregular_polygon_distance(self) -> None:
        irregular = zone("i", "IRREGULAR", "Irregular", [(0, 0), (80, 0), (120, 70), (30, 120), (-20, 50)])
        state = self.evaluate((140, 70), [irregular])
        self.assertEqual(GeofenceLevel.DANGER, state.level)
        self.assertAlmostEqual(20.0, state.distance_m or 0, places=3)

    def test_two_polygons_selects_nearest(self) -> None:
        other = zone("b", "RESTRICTED_B", "Restricted B", [(300, 0), (360, 0), (360, 60), (300, 60)])
        state = self.evaluate((280, 30), [self.square, other])
        self.assertEqual("RESTRICTED_B", state.zone_code)
        self.assertEqual(GeofenceLevel.DANGER, state.level)

    def test_inside_polygon_has_priority_over_nearby_polygon(self) -> None:
        nearby = zone("b", "RESTRICTED_B", "Restricted B", [(115, 0), (175, 0), (175, 60), (115, 60)])
        state = self.evaluate((50, 50), [self.square, nearby])
        self.assertEqual("RESTRICTED_A", state.zone_code)
        self.assertEqual(GeofenceLevel.VIOLATION, state.level)

    def test_replacing_polygon_cache_changes_detection_result(self) -> None:
        async def scenario():
            cache = RestrictedZoneCache()
            await cache.replace([self.square])
            old_state = evaluate_geofence((50, 50), await cache.snapshot(), caution_distance_m=50, danger_distance_m=20)
            moved = zone("a", "RESTRICTED_A", "Restricted A", [(200, 200), (300, 200), (300, 300), (200, 300)])
            await cache.replace([moved])
            new_state = evaluate_geofence((50, 50), await cache.snapshot(), caution_distance_m=50, danger_distance_m=20)
            return old_state, new_state

        old_state, new_state = asyncio.run(scenario())
        self.assertEqual(GeofenceLevel.VIOLATION, old_state.level)
        self.assertEqual(GeofenceLevel.SAFE, new_state.level)

    def test_empty_zone_list_is_safe(self) -> None:
        state = self.evaluate((50, 50), [])
        self.assertEqual(GeofenceLevel.SAFE, state.level)
        self.assertIsNone(state.zone_id)

    def test_invalid_geometry_from_backend_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            parse_restricted_zones(
                {
                    "data": [
                        {
                            "id": "bad",
                            "code": "BAD",
                            "name": "Bad",
                            "zoneType": "AIRPORT",
                            "coordinates": [[0, 0], [1, 1]],
                        }
                    ]
                }
            )

    def test_non_restricted_zone_is_ignored(self) -> None:
        zones = parse_restricted_zones(
            {
                "data": [
                    {
                        "id": "custom",
                        "code": "CUSTOM",
                        "name": "Custom",
                        "zoneType": "CUSTOM",
                        "coordinates": [[0, 0], [100, 0], [100, 100], [0, 100], [0, 0]],
                    }
                ]
            }
        )
        self.assertEqual([], zones)

    def test_px4_ned_to_sim_xy_mapping(self) -> None:
        self.assertEqual((20.0, -270.0), px4_ned_to_sim_xy(10.0, 20.0))

    def test_compact_airport_uses_px4_spawn_offset(self) -> None:
        airport = zone(
            "airport",
            "AIRPORT",
            "Airport",
            [
                (-305.3277885038878, -362.2201809613816),
                (-80.3277885038878, -362.2201809613816),
                (-80.3277885038878, -119.22018096138146),
                (-305.3277885038878, -119.22018096138146),
            ],
        )

        sim_point = px4_ned_to_sim_xy(40.0, -190.0)
        self.assertEqual((-190.0, -240.0), sim_point)
        state = self.evaluate(sim_point, [airport])

        self.assertEqual(GeofenceLevel.VIOLATION, state.level)
        self.assertTrue(state.inside)


if __name__ == "__main__":
    unittest.main()
