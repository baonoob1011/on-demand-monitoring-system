from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tools" / "blender_target_bounds.txt"


def world_xy_bounds(obj):
    corners = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    xs = [corner.x for corner in corners]
    ys = [corner.y for corner in corners]
    return min(xs), max(xs), min(ys), max(ys)


lines = ["NAME | TYPE | COLLECTIONS | LOCATION_X | LOCATION_Y | CENTER_X | CENTER_Y | MIN_X | MAX_X | MIN_Y | MAX_Y"]
for obj in sorted(bpy.context.scene.objects, key=lambda item: item.name.lower()):
    if not (obj.name.startswith("TARGET_") or obj.name.startswith("LBL_")):
        continue

    if obj.type == "MESH":
        min_x, max_x, min_y, max_y = world_xy_bounds(obj)
        center_x = (min_x + max_x) / 2.0
        center_y = (min_y + max_y) / 2.0
        bounds = f"{center_x:.3f} | {center_y:.3f} | {min_x:.3f} | {max_x:.3f} | {min_y:.3f} | {max_y:.3f}"
    else:
        bounds = "n/a | n/a | n/a | n/a | n/a | n/a"
    lines.append(
        f"{obj.name} | {obj.type} | {', '.join(collection.name for collection in obj.users_collection)} | "
        f"{obj.location.x:.3f} | {obj.location.y:.3f} | {bounds}"
    )

OUT.write_text("\n".join(lines), encoding="utf-8")
print("\n".join(lines))
print(f"Wrote {OUT}")
