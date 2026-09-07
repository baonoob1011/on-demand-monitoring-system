import json
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.blend"
REPORT = ROOT / "Forest3D" / "near_pad_objects_report.json"


def bounds(obj):
    mins = Vector((1e9, 1e9, 1e9))
    maxs = Vector((-1e9, -1e9, -1e9))
    for corner in obj.bound_box:
        world = obj.matrix_world @ Vector(corner)
        mins.x = min(mins.x, world.x)
        mins.y = min(mins.y, world.y)
        mins.z = min(mins.z, world.z)
        maxs.x = max(maxs.x, world.x)
        maxs.y = max(maxs.y, world.y)
        maxs.z = max(maxs.z, world.z)
    return mins, maxs


def main():
    bpy.ops.wm.open_mainfile(filepath=str(SRC))
    rows = []
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        mins, maxs = bounds(obj)
        center = Vector(((mins.x + maxs.x) * 0.5, (mins.y + maxs.y) * 0.5, (mins.z + maxs.z) * 0.5))
        size = maxs - mins
        if abs(center.x) <= 35 and abs(center.y) <= 35:
            rows.append({
                "name": obj.name,
                "location": [round(v, 3) for v in obj.location],
                "center": [round(v, 3) for v in center],
                "size": [round(v, 3) for v in size],
                "collections": [c.name for c in obj.users_collection],
                "material": [slot.material.name for slot in obj.material_slots if slot.material],
            })
    rows.sort(key=lambda r: (abs(r["center"][0]) + abs(r["center"][1]), r["name"]))
    REPORT.write_text(json.dumps(rows, indent=2), encoding="utf-8")
    print(json.dumps(rows[:120], indent=2))


if __name__ == "__main__":
    main()
