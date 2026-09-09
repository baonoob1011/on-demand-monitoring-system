import json
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tools" / "blender_zone_footprints.json"

ZONE_SELECTORS = {
    "DRONE_BASE": {"collections": ["ZONE_HOME"], "prefixes": ["SM_HOME_LandingPad", "SM_HOME_YellowRing", "SM_HOME_H_"]},
    "AIRPORT": {"collections": ["ZONE_AIRPORT"], "prefixes": ["V7_AIRPORT_"]},
    "DAM": {"collections": ["ZONE_DAM"], "prefixes": ["SM_Dam_", "POLISH_DAM_", "DAM_", "V4_Dam_", "Dam_Waterfall_", "Final_Dam_"]},
    "CONSTRUCTION_SITE": {"collections": ["ZONE_HIGHRISE"], "prefixes": ["SM_HR_", "POLISH_Highrise_", "V4_Highrise_"]},
    "AGRICULTURAL_FIELD": {"collections": ["ZONE_AGRICULTURE", "ZONE_AGRI"], "prefixes": ["SM_AG_"]},
    "INDUSTRIAL_WAREHOUSE": {"collections": ["ZONE_INDUSTRIAL"], "prefixes": ["SM_IND_", "POLISH_IND_"]},
    "LOGISTICS_YARD": {"collections": ["ZONE_LOGISTICS"], "prefixes": ["SM_LOG_", "POLISH_LOG_"]},
    "TELECOM_TOWER": {"collections": ["ZONE_TELECOM"], "prefixes": ["SM_TEL_"]},
    "LANDSLIDE_FLOOD_AREA": {"collections": ["ZONE_LANDSLIDE"], "prefixes": ["SM_LS_"]},
    "FOREST_MONITORING_AREA": {"collections": ["ZONE_FOREST", "FOREST"], "prefixes": ["SM_FOREST_", "FOREST_", "TREE_"]},
    "REMOTE_MONITORING_TARGET": {"collections": ["ZONE_REMOTE", "REMOTE_TARGET"], "prefixes": ["SM_REMOTE_", "REMOTE_"]},
}


def world_xy_bounds(obj):
    corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    xs = [corner.x for corner in corners]
    ys = [corner.y for corner in corners]
    zs = [corner.z for corner in corners]
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def object_collections(obj):
    return [collection.name for collection in obj.users_collection]


def matches(obj, selector):
    names = object_collections(obj)
    collection_hit = any(collection in names for collection in selector["collections"])
    prefix_hit = any(obj.name.startswith(prefix) for prefix in selector["prefixes"])
    ignored = obj.name.startswith(("ROAD_", "BAK_", "Final_Airport_Cleanup"))
    return (collection_hit or prefix_hit) and not ignored


objects = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]

scene_bounds = None
records = {}
for zone, selector in ZONE_SELECTORS.items():
    matched = []
    for obj in objects:
        if matches(obj, selector):
            min_x, max_x, min_y, max_y, min_z, max_z = world_xy_bounds(obj)
            matched.append(
                {
                    "name": obj.name,
                    "collections": object_collections(obj),
                    "minX": round(min_x, 3),
                    "maxX": round(max_x, 3),
                    "minY": round(min_y, 3),
                    "maxY": round(max_y, 3),
                    "minZ": round(min_z, 3),
                    "maxZ": round(max_z, 3),
                }
            )

    if matched:
        min_x = min(item["minX"] for item in matched)
        max_x = max(item["maxX"] for item in matched)
        min_y = min(item["minY"] for item in matched)
        max_y = max(item["maxY"] for item in matched)
        records[zone] = {
            "objects": matched,
            "centerX": round((min_x + max_x) / 2.0, 3),
            "centerY": round((min_y + max_y) / 2.0, 3),
            "minX": min_x,
            "maxX": max_x,
            "minY": min_y,
            "maxY": max_y,
        }
    else:
        records[zone] = {"objects": []}

for obj in objects:
    bounds = world_xy_bounds(obj)
    if scene_bounds is None:
        scene_bounds = list(bounds[:4])
    else:
        scene_bounds[0] = min(scene_bounds[0], bounds[0])
        scene_bounds[1] = max(scene_bounds[1], bounds[1])
        scene_bounds[2] = min(scene_bounds[2], bounds[2])
        scene_bounds[3] = max(scene_bounds[3], bounds[3])

payload = {
    "sceneBounds": {
        "minX": round(scene_bounds[0], 3),
        "maxX": round(scene_bounds[1], 3),
        "minY": round(scene_bounds[2], 3),
        "maxY": round(scene_bounds[3], 3),
    },
    "zones": records,
}

OUT.write_text(json.dumps(payload, indent=2), encoding="utf-8")

print("ZONE | OBJECTS | CENTER_X | CENTER_Y | MIN_X | MAX_X | MIN_Y | MAX_Y")
for zone, record in records.items():
    names = ", ".join(item["name"] for item in record["objects"][:8])
    if len(record["objects"]) > 8:
        names += f", ... (+{len(record['objects']) - 8})"
    print(
        f"{zone} | {names or 'NO MATCH'} | "
        f"{record.get('centerX', 'n/a')} | {record.get('centerY', 'n/a')} | "
        f"{record.get('minX', 'n/a')} | {record.get('maxX', 'n/a')} | "
        f"{record.get('minY', 'n/a')} | {record.get('maxY', 'n/a')}"
    )
print(f"Wrote {OUT}")
