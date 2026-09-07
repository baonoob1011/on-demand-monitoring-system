import json
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.blend"
MODEL_ROOT = ROOT / "Forest3D" / "models"
REPORT = ROOT / "Forest3D" / "compact_export_report.json"

GROUPS = [
    "compact_terrain",
    "compact_water",
    "compact_roads",
    "compact_bridges",
    "compact_home",
    "compact_highrise",
    "compact_zones",
    "compact_forest",
    "compact_environment_props",
]


def classify(obj):
    name = obj.name.lower()
    cols = " ".join(c.name.lower() for c in obj.users_collection)
    text = f"{name} {cols}"
    if any(k in text for k in ["terrain", "diorama", "mountain", "ground", "bank"]):
        return "compact_terrain"
    if any(k in text for k in ["river", "water", "reservoir", "foam", "rapid"]):
        return "compact_water"
    if any(k in text for k in ["road", "asphalt", "marking", "shoulder", "street"]):
        return "compact_roads"
    if any(k in text for k in ["bridge", "pier", "abutment", "guardrail"]):
        return "compact_bridges"
    if any(k in text for k in ["landing", "home", "pad"]):
        return "compact_home"
    if any(k in text for k in ["highrise", "construction", "crane", "rebar", "concretepipe", "rw_"]):
        return "compact_highrise"
    if any(k in text for k in ["tree", "forest", "pine", "foliage", "grass", "bush"]):
        return "compact_forest"
    if any(k in text for k in ["industrial", "logistics", "agriculture", "power", "telecom", "dam", "landslide", "warehouse", "factory", "container", "tower", "radio"]):
        return "compact_zones"
    return "compact_environment_props"


def object_bounds(objects):
    mins = Vector((1e9, 1e9, 1e9))
    maxs = Vector((-1e9, -1e9, -1e9))
    for obj in objects:
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

    exportable = []
    for obj in bpy.context.scene.objects:
        if obj.type != "MESH":
            continue
        if obj.hide_render or obj.hide_viewport:
            continue
        if obj.name.startswith(("TARGET_", "LBL_", "CAM_")):
            continue
        exportable.append(obj)

    mins, maxs = object_bounds(exportable)
    home = (0.0, 0.0, 0.3)
    for obj in exportable:
        if "landing" in obj.name.lower() or "home" in obj.name.lower():
            home = (round(obj.location.x, 3), round(obj.location.y, 3), round(obj.location.z + 0.45, 3))
            break

    exports = {}
    counts = {}
    for group in GROUPS:
        group_dir = MODEL_ROOT / group
        mesh_dir = group_dir / "meshes"
        mesh_dir.mkdir(parents=True, exist_ok=True)
        selected = [obj for obj in exportable if classify(obj) == group]
        counts[group] = len(selected)
        if not selected:
            continue
        bpy.ops.object.select_all(action="DESELECT")
        for obj in selected:
            obj.select_set(True)
        bpy.context.view_layer.objects.active = selected[0]
        out = mesh_dir / f"{group}.glb"
        bpy.ops.export_scene.gltf(
            filepath=str(out),
            export_format="GLB",
            use_selection=True,
            export_apply=True,
            export_yup=False,
            export_materials="EXPORT",
        )
        exports[group] = str(out)

    report = {
        "source": str(SRC),
        "format": "GLB",
        "sdf_version": "1.9",
        "bounds": {
            "min": [round(v, 3) for v in mins],
            "max": [round(v, 3) for v in maxs],
            "size": [round(maxs[i] - mins[i], 3) for i in range(3)],
        },
        "home_blender": home,
        "home_gazebo": home,
        "px4_spawn": [home[0], home[1], home[2], 0, 0, 0],
        "counts": counts,
        "exports": exports,
    }
    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
