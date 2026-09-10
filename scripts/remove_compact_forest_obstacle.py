import json
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.blend"
BACKUP = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.before-tree-clear.blend"
FOREST_GLB = ROOT / "Forest3D" / "models" / "compact_forest" / "meshes" / "compact_forest.glb"
REPORT = ROOT / "Forest3D" / "compact_forest_obstacle_removal_report.json"


def classify(obj):
    name = obj.name.lower()
    cols = " ".join(c.name.lower() for c in obj.users_collection)
    text = f"{name} {cols}"
    return any(k in text for k in ["tree", "forest", "pine", "foliage", "grass", "bush"])


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


def in_flight_clear_zone(obj):
    mins, maxs = bounds(obj)
    cx = (mins.x + maxs.x) * 0.5
    cy = (mins.y + maxs.y) * 0.5
    sx = maxs.x - mins.x
    sy = maxs.y - mins.y
    sz = maxs.z - mins.z

    # Clear only obvious tree-sized vertical vegetation on / near the landing pad cross.
    near_center = abs(cx) <= 9.0 and abs(cy) <= 9.0
    vertical_tree = sz >= 1.0 and max(sx, sy) <= 8.0
    return near_center and vertical_tree


def main():
    bpy.ops.wm.open_mainfile(filepath=str(SRC))

    if not BACKUP.exists():
        bpy.ops.wm.save_as_mainfile(filepath=str(BACKUP))

    exportable = []
    removed = []
    candidates = []

    for obj in list(bpy.context.scene.objects):
        if obj.type != "MESH" or obj.hide_render or obj.hide_viewport:
            continue
        if not classify(obj):
            continue

        mins, maxs = bounds(obj)
        center = ((mins.x + maxs.x) * 0.5, (mins.y + maxs.y) * 0.5, (mins.z + maxs.z) * 0.5)
        size = (maxs.x - mins.x, maxs.y - mins.y, maxs.z - mins.z)
        if abs(center[0]) <= 15.0 and abs(center[1]) <= 15.0:
            candidates.append({"name": obj.name, "center": [round(v, 3) for v in center], "size": [round(v, 3) for v in size]})

        if in_flight_clear_zone(obj):
            removed.append({"name": obj.name, "center": [round(v, 3) for v in center], "size": [round(v, 3) for v in size]})
            bpy.data.objects.remove(obj, do_unlink=True)
            continue

        exportable.append(obj)

    bpy.ops.wm.save_as_mainfile(filepath=str(SRC))

    bpy.ops.object.select_all(action="DESELECT")
    for obj in exportable:
        obj.select_set(True)
    if exportable:
        bpy.context.view_layer.objects.active = exportable[0]
        bpy.ops.export_scene.gltf(
            filepath=str(FOREST_GLB),
            export_format="GLB",
            use_selection=True,
            export_apply=True,
            export_yup=False,
            export_materials="EXPORT",
        )

    report = {
        "source": str(SRC),
        "backup": str(BACKUP),
        "export": str(FOREST_GLB),
        "candidates_near_pad_before_removal": candidates,
        "removed": removed,
        "remaining_forest_objects": len(exportable),
    }
    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
