import json
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "Forest3D" / "green_pad_objects_report.json"


def world_bounds(obj):
    pts = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    mins = [min(p[i] for p in pts) for i in range(3)]
    maxs = [max(p[i] for p in pts) for i in range(3)]
    center = [(mins[i] + maxs[i]) / 2.0 for i in range(3)]
    size = [maxs[i] - mins[i] for i in range(3)]
    return center, size, mins, maxs


def material_names(obj):
    return [slot.material.name for slot in obj.material_slots if slot.material]


def is_greenish_material(name):
    lowered = name.lower()
    return any(token in lowered for token in ("green", "leaf", "leaves", "pine", "tree", "forest", "foliage", "canopy"))


rows = []
for obj in bpy.context.scene.objects:
    if obj.type != "MESH":
        continue

    mats = material_names(obj)
    name_hit = any(token in obj.name.lower() for token in ("tree", "pine", "forest", "canopy", "leaf", "leaves", "cone"))
    mat_hit = any(is_greenish_material(m) for m in mats)
    if not (name_hit or mat_hit):
        continue

    center, size, mins, maxs = world_bounds(obj)
    # Keep a generous area around the landing pad / spawn path.
    if abs(center[0]) > 80 or abs(center[1]) > 80:
        continue

    rows.append(
        {
            "name": obj.name,
            "location": [round(v, 3) for v in obj.location],
            "center": [round(v, 3) for v in center],
            "size": [round(v, 3) for v in size],
            "min": [round(v, 3) for v in mins],
            "max": [round(v, 3) for v in maxs],
            "collections": [c.name for c in obj.users_collection],
            "materials": mats,
        }
    )

rows.sort(key=lambda r: (abs(r["center"][0]) + abs(r["center"][1]), r["name"]))
OUT.write_text(json.dumps(rows, indent=2), encoding="utf-8")

print(f"Wrote {len(rows)} green candidates to {OUT}")
for row in rows[:80]:
    print(row)
