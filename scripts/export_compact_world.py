import json
import struct
import time
import xml.etree.ElementTree as ET
from pathlib import Path

import bpy
from mathutils import Vector


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "Blender" / "uav-monitoring-compact-world-v7.blend"
MODEL_ROOT = ROOT / "Forest3D" / "models"
REPORT = ROOT / "Forest3D" / "compact_export_report.json"
MANIFEST = ROOT / "Forest3D" / "compact_export_manifest.json"
WORLD = ROOT / "Forest3D" / "worlds" / "forest_monitoring_compact.sdf"

GROUPS = [
    "compact_terrain",
    "compact_mountains",
    "compact_water",
    "compact_airport",
    "compact_roads",
    "compact_bridges",
    "compact_home",
    "compact_highrise",
    "compact_zones",
    "compact_forest",
    "compact_environment_props",
]

COLLISION_GROUPS = {
    "compact_terrain",
    "compact_home",
    "compact_airport",
    "compact_roads",
}


def classify(obj):
    name = obj.name.lower()
    cols = " ".join(c.name.lower() for c in obj.users_collection)
    text = f"{name} {cols}"
    if any(k in text for k in ["env_mountainrange", "mountain_", "foothill_", "mr_tree_", "mr_base_rock_"]):
        return "compact_mountains"
    if any(k in text for k in ["airport", "runway", "taxiway", "apron", "hangar"]):
        return "compact_airport"
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


def object_manifest(obj):
    mins, maxs = object_bounds([obj])
    mesh = obj.data if obj.type == "MESH" else None
    return {
        "name": obj.name,
        "collections": [c.name for c in obj.users_collection],
        "type": obj.type,
        "visible_viewport": not obj.hide_viewport,
        "visible_render": not obj.hide_render,
        "location": [round(v, 6) for v in obj.location],
        "rotation_euler": [round(v, 6) for v in obj.rotation_euler],
        "scale": [round(v, 6) for v in obj.scale],
        "bounds": {
            "min": [round(v, 6) for v in mins],
            "max": [round(v, 6) for v in maxs],
            "size": [round(maxs[i] - mins[i], 6) for i in range(3)],
            "center": [round((mins[i] + maxs[i]) / 2, 6) for i in range(3)],
        },
        "materials": [m.name for m in mesh.materials if m] if mesh else [],
        "mesh": {
            "name": mesh.name,
            "vertices": len(mesh.vertices),
            "polygons": len(mesh.polygons),
            "has_modifiers": bool(obj.modifiers),
            "modifiers": [mod.type for mod in obj.modifiers],
        } if mesh else None,
        "parent": obj.parent.name if obj.parent else None,
        "group": classify(obj),
        "export": True,
    }


def write_model_files(model_name, has_mesh):
    group_dir = MODEL_ROOT / model_name
    group_dir.mkdir(parents=True, exist_ok=True)
    (group_dir / "model.config").write_text(
        f'<?xml version="1.0"?>\n'
        f"<model><name>{model_name}</name><version>1.0</version>"
        f'<sdf version="1.9">model.sdf</sdf></model>\n',
        encoding="utf-8",
    )
    visual = ""
    if has_mesh:
        visual = (
            "      <visual name=\"visual\">\n"
            f"        <geometry><mesh><uri>model://{model_name}/meshes/{model_name}.glb</uri></mesh></geometry>\n"
            "      </visual>\n"
        )
    collision = ""
    if has_mesh and model_name in COLLISION_GROUPS:
        collision = (
            "      <collision name=\"collision\">\n"
            f"        <geometry><mesh><uri>model://{model_name}/meshes/{model_name}.glb</uri></mesh></geometry>\n"
            "      </collision>\n"
        )
    (group_dir / "model.sdf").write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<sdf version=\"1.9\">\n"
        f"  <model name=\"{model_name}\">\n"
        "    <static>true</static>\n"
        "    <link name=\"link\">\n"
        f"{visual}"
        f"{collision}"
        "    </link>\n"
        "  </model>\n"
        "</sdf>\n",
        encoding="utf-8",
    )


def glb_summary(path):
    data = Path(path).read_bytes()
    if len(data) < 20:
        return {"exists": True, "valid_glb": False, "bytes": len(data), "error": "too_small"}
    magic, version, length = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        return {"exists": True, "valid_glb": False, "bytes": len(data), "error": "bad_header"}
    chunk_len, chunk_type = struct.unpack_from("<I4s", data, 12)
    if chunk_type != b"JSON":
        return {"exists": True, "valid_glb": False, "bytes": len(data), "error": "missing_json_chunk"}
    doc = json.loads(data[20:20 + chunk_len].decode("utf-8").rstrip("\x00 "))
    meshes = doc.get("meshes", [])
    nodes = doc.get("nodes", [])
    accessors = doc.get("accessors", [])
    total_vertices = 0
    total_primitives = 0
    for mesh in meshes:
        for prim in mesh.get("primitives", []):
            total_primitives += 1
            pos = prim.get("attributes", {}).get("POSITION")
            if isinstance(pos, int) and pos < len(accessors):
                total_vertices += int(accessors[pos].get("count", 0))
    return {
        "exists": True,
        "valid_glb": True,
        "bytes": len(data),
        "declared_length": length,
        "meshes": len(meshes),
        "nodes": len(nodes),
        "primitives": total_primitives,
        "vertices": total_vertices,
        "materials": len(doc.get("materials", [])),
    }


def write_world():
    tree = ET.parse(WORLD)
    root = tree.getroot()
    world = root.find("world")
    existing = {inc.findtext("name") or inc.findtext("uri", "").replace("model://", ""): inc for inc in world.findall("include")}
    for group in GROUPS:
        if group in existing:
            continue
        include = ET.SubElement(world, "include")
        ET.SubElement(include, "name").text = group
        ET.SubElement(include, "uri").text = f"model://{group}"
    ET.indent(tree, space="  ")
    tree.write(WORLD, encoding="utf-8", xml_declaration=True)


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
    px4_spawn = (0.0, 0.0, 0.3)
    for obj in exportable:
        if "landing" in obj.name.lower() or "home" in obj.name.lower():
            mins_home, maxs_home = object_bounds([obj])
            home = (round(obj.location.x, 3), round(obj.location.y, 3), round(maxs_home.z, 3))
            px4_spawn = (home[0], home[1], round(home[2] + 0.9, 3))
            break

    exports = {}
    counts = {}
    manifest = {
        "source": str(SRC),
        "generated_at_unix": int(time.time()),
        "objects": [],
        "groups": {},
        "critical": {},
    }
    water_objects = []
    critical_terms = {
        "terrain": ["terrain"],
        "water": ["water", "river", "reservoir", "foam"],
        "dam": ["dam"],
        "airport": ["airport", "runway", "taxiway", "apron"],
        "mountains": ["mountain", "foothill", "mr_tree", "mr_base_rock"],
        "industrial": ["industrial", "factory"],
        "construction": ["construction", "highrise", "crane"],
        "helipad_home": ["landing", "home", "pad"],
        "logistics": ["logistics", "warehouse"],
        "telecom": ["telecom", "radio"],
        "landslide": ["landslide"],
        "roads": ["road"],
        "forest": ["forest", "tree", "pine"],
    }
    for obj in exportable:
        entry = object_manifest(obj)
        manifest["objects"].append(entry)
        text = f"{obj.name.lower()} {' '.join(c.name.lower() for c in obj.users_collection)} {' '.join(entry['materials']).lower()}"
        if entry["group"] == "compact_water" or "water" in text or "river" in text or "reservoir" in text:
            water_objects.append(entry)
        for label, terms in critical_terms.items():
            if any(term in text for term in terms):
                manifest["critical"].setdefault(label, []).append(obj.name)
    for group in GROUPS:
        group_dir = MODEL_ROOT / group
        mesh_dir = group_dir / "meshes"
        mesh_dir.mkdir(parents=True, exist_ok=True)
        selected = [obj for obj in exportable if classify(obj) == group]
        counts[group] = len(selected)
        manifest["groups"][group] = [obj.name for obj in selected]
        if not selected:
            stale = mesh_dir / f"{group}.glb"
            if stale.exists():
                stale.unlink()
            write_model_files(group, has_mesh=False)
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
        write_model_files(group, has_mesh=True)
    write_world()

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
        "px4_spawn": [px4_spawn[0], px4_spawn[1], px4_spawn[2], 0, 0, 0],
        "counts": counts,
        "exports": exports,
        "water_objects": [w["name"] for w in water_objects],
        "dam_waterfalls": [obj.name for obj in exportable if obj.name.startswith("Dam_Waterfall_")],
        "mesh_validation": {group: glb_summary(path) for group, path in exports.items()},
        "world": str(WORLD),
    }
    manifest["report"] = report
    REPORT.write_text(json.dumps(report, indent=2), encoding="utf-8")
    MANIFEST.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
