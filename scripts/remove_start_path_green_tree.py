import json
import shutil
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.blend"
BACKUP = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.before-start-tree-removal.blend"
HOME_GLB = ROOT / "Forest3D" / "models" / "compact_home" / "meshes" / "compact_home.glb"
REPORT = ROOT / "Forest3D" / "start_path_green_tree_removal_report.json"


def bounds(obj):
    pts = [obj.matrix_world @ Vector(corner) for corner in obj.bound_box]
    mins = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    maxs = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    return mins, maxs


def names(items):
    return [item.name for item in items if item]


def is_green_start_obstacle(obj):
    mats = names(slot.material for slot in obj.material_slots)
    cols = names(obj.users_collection)
    text = " ".join([obj.name, *mats, *cols]).lower()
    if not any(token in text for token in ("green", "grass", "tree", "pine", "forest", "foliage", "leaf", "shrub")):
        return False

    mins, maxs = bounds(obj)
    center = (mins + maxs) * 0.5
    size = maxs - mins

    near_start = -35.0 <= center.x <= 35.0 and -330.0 <= center.y <= -230.0
    visible_tree_shape = size.z >= 3.0 and max(size.x, size.y) >= 3.0
    return near_start and visible_tree_shape


def export_collections_glb(collection_names, path):
    bpy.ops.object.select_all(action="DESELECT")
    exportable = []
    for collection_name in collection_names:
        collection = bpy.data.collections.get(collection_name)
        if collection is None:
            raise RuntimeError(f"Missing collection: {collection_name}")
        exportable.extend(obj for obj in collection.objects if obj.type == "MESH" and not obj.hide_render)

    for obj in exportable:
        obj.select_set(True)
    if not exportable:
        raise RuntimeError(f"No exportable mesh objects in {collection_names}")

    bpy.context.view_layer.objects.active = exportable[0]
    bpy.ops.export_scene.gltf(
        filepath=str(path),
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_yup=False,
        export_materials="EXPORT",
    )
    return len(exportable)


def main():
    if not BACKUP.exists():
        shutil.copy2(SRC, BACKUP)

    bpy.ops.wm.open_mainfile(filepath=str(SRC))

    candidates = []
    removed = []
    for obj in list(bpy.context.scene.objects):
        if obj.type != "MESH":
            continue

        mins, maxs = bounds(obj)
        center = (mins + maxs) * 0.5
        size = maxs - mins
        mats = names(slot.material for slot in obj.material_slots)
        cols = names(obj.users_collection)

        if -45.0 <= center.x <= 45.0 and -340.0 <= center.y <= -220.0:
            candidates.append(
                {
                    "name": obj.name,
                    "center": [round(center.x, 3), round(center.y, 3), round(center.z, 3)],
                    "size": [round(size.x, 3), round(size.y, 3), round(size.z, 3)],
                    "collections": cols,
                    "materials": mats,
                }
            )

        if is_green_start_obstacle(obj):
            removed.append(
                {
                    "name": obj.name,
                    "center": [round(center.x, 3), round(center.y, 3), round(center.z, 3)],
                    "size": [round(size.x, 3), round(size.y, 3), round(size.z, 3)],
                    "collections": cols,
                    "materials": mats,
                }
            )
            bpy.data.objects.remove(obj, do_unlink=True)

    bpy.ops.wm.save_as_mainfile(filepath=str(SRC))
    exported_home_objects = export_collections_glb(["ZONE_HOME", "DETAIL_HOME"], HOME_GLB)

    report = {
        "source": str(SRC),
        "backup": str(BACKUP),
        "export": str(HOME_GLB),
        "candidates_near_start_before_removal": candidates,
        "removed": removed,
        "exported_collections": ["ZONE_HOME", "DETAIL_HOME"],
        "exported_home_objects": exported_home_objects,
    }
    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
