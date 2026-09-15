import json
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "Forest3D" / "compact_export_report.json"
OUT_DIR = ROOT / "ondemandmonitoring" / "src" / "main" / "resources" / "static" / "simulation-viewer"
OUT_IMAGE = OUT_DIR / "simulation_map_top.png"
OUT_META = OUT_DIR / "simulation-map.json"


def read_bounds():
    report = json.loads(REPORT.read_text(encoding="utf-8"))
    bounds = report["bounds"]
    min_x, min_y, _ = bounds["min"]
    max_x, max_y, _ = bounds["max"]
    return report, float(min_x), float(max_x), float(min_y), float(max_y)


def configure_scene(min_x, max_x, min_y, max_y):
    width = max_x - min_x
    height = max_y - min_y
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0

    bpy.ops.object.light_add(type="SUN", location=(center_x, center_y, 700))
    sun = bpy.context.object
    sun.name = "TMP_SimulationTopMap_Sun"
    sun.data.energy = 2.5
    sun.rotation_euler = (0.0, 0.0, 0.0)

    bpy.ops.object.camera_add(location=(center_x, center_y, 1200), rotation=(0.0, 0.0, 0.0))
    camera = bpy.context.object
    camera.name = "TMP_SimulationTopMap_Camera"
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = max(width, height) * 1.04
    camera.data.clip_end = 5000
    bpy.context.scene.camera = camera

    scene = bpy.context.scene
    colorize_for_top_map()

    scene.render.engine = "BLENDER_WORKBENCH"
    scene.render.resolution_x = 2048
    scene.render.resolution_y = 2048
    scene.display.shading.light = "STUDIO"
    scene.display.shading.color_type = "MATERIAL"
    scene.display.shading.show_cavity = True
    scene.display.shading.show_object_outline = True
    scene.view_settings.view_transform = "Filmic"
    scene.view_settings.look = "Medium High Contrast"
    scene.view_settings.exposure = 0.0
    scene.view_settings.gamma = 1.0
    scene.render.film_transparent = False
    scene.world.color = (0.78, 0.83, 0.88)
    scene.render.filepath = str(OUT_IMAGE)


def material(name, color):
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
    mat.diffuse_color = color
    return mat


def object_groups(obj):
    names = {collection.name.lower() for collection in obj.users_collection}
    names.add(obj.name.lower())
    return " ".join(names)


def assign_material(obj, mat):
    obj.data.materials.clear()
    obj.data.materials.append(mat)


def colorize_for_top_map():
    mats = {
        "terrain": material("MAT_TopMap_Terrain", (0.50, 0.70, 0.45, 1.0)),
        "mountain": material("MAT_TopMap_Mountain", (0.58, 0.55, 0.50, 1.0)),
        "water": material("MAT_TopMap_Water", (0.07, 0.45, 0.72, 1.0)),
        "road": material("MAT_TopMap_Road", (0.12, 0.13, 0.14, 1.0)),
        "airport": material("MAT_TopMap_Airport", (0.86, 0.82, 0.65, 1.0)),
        "home": material("MAT_TopMap_Home", (0.95, 0.84, 0.24, 1.0)),
        "forest": material("MAT_TopMap_Forest", (0.05, 0.36, 0.14, 1.0)),
        "building": material("MAT_TopMap_Building", (0.80, 0.83, 0.84, 1.0)),
        "zone": material("MAT_TopMap_Zone", (0.95, 0.56, 0.12, 1.0)),
        "prop": material("MAT_TopMap_Prop", (0.65, 0.57, 0.48, 1.0)),
    }

    for obj in bpy.context.scene.objects:
        if obj.type != "MESH" or obj.hide_render:
            continue

        group = object_groups(obj)
        if "water" in group or "river" in group or "reservoir" in group:
            assign_material(obj, mats["water"])
        elif "road" in group or "asphalt" in group or "street" in group:
            assign_material(obj, mats["road"])
        elif "airport" in group or "runway" in group:
            assign_material(obj, mats["airport"])
        elif "home" in group or "landing" in group or "pad" in group:
            assign_material(obj, mats["home"])
        elif "forest" in group or "tree" in group or "pine" in group:
            assign_material(obj, mats["forest"])
        elif "mountain" in group or "rock" in group:
            assign_material(obj, mats["mountain"])
        elif "zone" in group or "fence" in group:
            assign_material(obj, mats["zone"])
        elif "highrise" in group or "building" in group or "warehouse" in group or "industrial" in group:
            assign_material(obj, mats["building"])
        elif "terrain" in group or "ground" in group:
            assign_material(obj, mats["terrain"])
        else:
            assign_material(obj, mats["prop"])


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    report, min_x, max_x, min_y, max_y = read_bounds()
    configure_scene(min_x, max_x, min_y, max_y)
    bpy.ops.render.render(write_still=True)

    metadata = {
        "image": "/simulation-viewer/simulation_map_top.png",
        "sourceBlender": report["source"],
        "sourceWorld": str(ROOT / "Forest3D" / "worlds" / "forest_monitoring_compact.sdf"),
        "worldName": "forest_monitoring_compact",
        "minX": min_x,
        "maxX": max_x,
        "minY": min_y,
        "maxY": max_y,
        "widthM": max_x - min_x,
        "heightM": max_y - min_y,
        "coordinateSystem": "LOCAL_SIMULATION_METERS_GAZEBO_XY",
        "imageOrientation": {
            "xIncreasesRight": True,
            "yIncreasesUp": True,
            "browserYInverted": True,
            "xySwapped": False,
            "rotationDegrees": 0
        },
        "px4Mapping": {
            "gazeboX": "PX4 local East axis",
            "gazeboY": "PX4 local North axis",
            "px4NedNorth": "simulation Y",
            "px4NedEast": "simulation X"
        }
    }
    OUT_META.write_text(json.dumps(metadata, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
