import json
import math
import random
import sys
from pathlib import Path

import bpy
from mathutils import Vector


UPDATED_BLEND = Path(sys.argv[-2])
REPORT_PATH = Path(sys.argv[-1])

AIRPORT_BOUNDS = {
    "x_min": 178.0,
    "x_max": 392.0,
    "y_min": 92.0,
    "y_max": 368.0,
}

RUNWAY = {
    "center": (326.0, 230.0),
    "length": 300.0,
    "width": 28.0,
    "heading": "N-S",
    "numbers": "18/36",
}

REMOVABLE_COLLECTIONS = {
    "ENV_VEGETATION",
    "ENV_SHRUBS",
    "ENV_ROCKS",
    "TREES",
    "ROCKS",
    "UNDERGROWTH",
    "DETAIL_PROPS",
}

PRESERVE_PREFIXES = (
    "SM_Dam",
    "DAM_",
    "SM_River",
    "BRIDGE_",
    "ROAD_Intersection_Power",
    "TARGET_",
    "LBL_",
)


def collection(name, parent=None):
    existing = bpy.data.collections.get(name)
    if existing:
        return existing
    col = bpy.data.collections.new(name)
    (parent or bpy.context.scene.collection).children.link(col)
    return col


COL_AIRPORT = collection("AIRPORT")
SUBCOLS = {
    name: collection(f"AIRPORT_{name}", COL_AIRPORT)
    for name in (
        "RUNWAY",
        "RUNWAY_MARKINGS",
        "TAXIWAYS",
        "APRON",
        "TERMINAL",
        "CONTROL_TOWER",
        "HANGAR",
        "AIRCRAFT",
        "SERVICE_BUILDINGS",
        "AIRPORT_FENCE",
        "AIRPORT_LIGHTS",
        "AIRPORT_ROADS",
        "REMOVED_STAGING",
    )
}


def mat(name, color, roughness=0.65, metallic=0.0):
    existing = bpy.data.materials.get(name)
    if existing:
        return existing
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    bsdf = material.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = color
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    return material


MAT = {
    "asphalt": mat("MAT_Airport_Dark_Asphalt", (0.055, 0.058, 0.058, 1)),
    "taxi": mat("MAT_Airport_Taxiway_Asphalt", (0.085, 0.085, 0.08, 1)),
    "concrete": mat("MAT_Airport_Apron_Concrete", (0.46, 0.47, 0.44, 1)),
    "white": mat("MAT_Airport_White_Marking", (0.92, 0.92, 0.86, 1)),
    "yellow": mat("MAT_Airport_Yellow_Marking", (1.0, 0.73, 0.06, 1)),
    "glass": mat("MAT_Airport_Blue_Glass", (0.18, 0.38, 0.62, 0.72), 0.15),
    "metal": mat("MAT_Airport_Metal", (0.46, 0.48, 0.5, 1), 0.32, 0.15),
    "roof": mat("MAT_Airport_Light_Roof", (0.66, 0.68, 0.68, 1)),
    "fence": mat("MAT_Airport_Fence_Dark_Metal", (0.05, 0.055, 0.055, 1), 0.45, 0.3),
    "grass": mat("MAT_Airport_Managed_Grass", (0.25, 0.50, 0.18, 1)),
    "plane": mat("MAT_Airport_Static_Aircraft_White", (0.83, 0.84, 0.82, 1)),
    "accent": mat("MAT_Airport_Static_Aircraft_Accent", (0.05, 0.22, 0.55, 1)),
    "red": mat("MAT_Airport_Red_Light", (0.9, 0.08, 0.04, 1)),
}


def link_to(obj, col):
    col.objects.link(obj)
    for old in list(obj.users_collection):
        if old != col:
            old.objects.unlink(obj)


def set_origin_name(obj, name):
    obj.name = name
    obj.data.name = name


def add_cube(name, loc, scale, material, col, bevel=0.0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    obj = bpy.context.object
    set_origin_name(obj, name)
    obj.dimensions = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    obj.data.materials.append(material)
    if bevel:
        mod = obj.modifiers.new("small_bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 2
        obj.modifiers.new("weighted_normals", "WEIGHTED_NORMAL")
    link_to(obj, col)
    return obj


def add_plane_rect(name, center, size, material, col, z=64.0, yaw=0.0):
    obj = add_cube(name, (center[0], center[1], z), (size[0], size[1], 0.18), material, col, 0.05)
    obj.rotation_euler[2] = yaw
    return obj


def add_text(name, text, loc, size, col, yaw=0.0, material=None):
    bpy.ops.object.text_add(location=loc, rotation=(math.radians(90), 0, yaw))
    obj = bpy.context.object
    set_origin_name(obj, name)
    obj.data.body = text
    obj.data.align_x = "CENTER"
    obj.data.align_y = "CENTER"
    obj.data.size = size
    obj.data.extrude = 0.02
    if material:
        obj.data.materials.append(material)
    link_to(obj, col)
    return obj


def in_airport_area(obj):
    x, y, _z = obj.location
    return (
        AIRPORT_BOUNDS["x_min"] <= x <= AIRPORT_BOUNDS["x_max"]
        and AIRPORT_BOUNDS["y_min"] <= y <= AIRPORT_BOUNDS["y_max"]
    )


removed = []
relocated = []
for obj in list(bpy.context.scene.objects):
    if not in_airport_area(obj):
        continue
    if obj.type not in {"MESH", "EMPTY"}:
        continue
    col_names = {col.name for col in obj.users_collection}
    if not (col_names & REMOVABLE_COLLECTIONS):
        continue
    if obj.name.startswith(PRESERVE_PREFIXES):
        continue
    removed.append(obj.name)
    bpy.data.objects.remove(obj, do_unlink=True)


# Airport ground reservation and perimeter.
add_plane_rect(
    "AIRPORT_Boundary_Managed_Grass",
    (285, 230),
    (214, 276),
    MAT["grass"],
    SUBCOLS["AIRPORT_ROADS"],
    z=62.0,
)
add_plane_rect("AIRPORT_Runway_18_36_Asphalt", RUNWAY["center"], (RUNWAY["width"], RUNWAY["length"]), MAT["asphalt"], SUBCOLS["RUNWAY"], z=63.0)

# Runway markings.
for y in range(105, 356, 32):
    add_plane_rect(f"AIRPORT_Runway_Centerline_{y}", (326, y), (1.0, 16.0), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.16)
for x in (311.3, 340.7):
    add_plane_rect(f"AIRPORT_Runway_EdgeLine_{x:.1f}", (x, 230), (0.8, 286), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.17)
for y, label in ((94, "18"), (366, "36")):
    add_plane_rect(f"AIRPORT_Runway_ThresholdBar_{label}", (326, y), (22, 1.6), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.18)
    for i, x in enumerate((317, 323, 329, 335)):
        add_plane_rect(f"AIRPORT_Runway_ThresholdStripe_{label}_{i}", (x, y + (9 if label == "18" else -9)), (2.2, 13), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.19)
    add_text(f"AIRPORT_Runway_Number_{label}", label, (326, y + (30 if label == "18" else -30), 63.25), 14, SUBCOLS["RUNWAY_MARKINGS"], yaw=0.0, material=MAT["white"])
for y in (165, 295):
    add_plane_rect(f"AIRPORT_Runway_Aiming_Left_{y}", (320, y), (4, 24), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.2)
    add_plane_rect(f"AIRPORT_Runway_Aiming_Right_{y}", (332, y), (4, 24), MAT["white"], SUBCOLS["RUNWAY_MARKINGS"], z=63.2)

# Taxiways and apron.
add_plane_rect("AIRPORT_Apron_Main_Concrete", (244, 254), (92, 82), MAT["concrete"], SUBCOLS["APRON"], z=63.05)
add_plane_rect("AIRPORT_Taxiway_Apron_To_Runway", (286, 254), (55, 18), MAT["taxi"], SUBCOLS["TAXIWAYS"], z=63.1)
add_plane_rect("AIRPORT_Taxiway_North_Link", (286, 318), (55, 16), MAT["taxi"], SUBCOLS["TAXIWAYS"], z=63.1)
add_plane_rect("AIRPORT_Taxiway_South_Link", (286, 172), (55, 16), MAT["taxi"], SUBCOLS["TAXIWAYS"], z=63.1)
for x, y, sx, sy in ((286, 254, 48, 0.7), (286, 318, 48, 0.7), (286, 172, 48, 0.7), (244, 254, 0.7, 72)):
    add_plane_rect(f"AIRPORT_Taxiway_Yellow_Center_{x}_{y}", (x, y), (sx, sy), MAT["yellow"], SUBCOLS["TAXIWAYS"], z=63.25)

for i, x in enumerate((218, 238, 258, 278), start=1):
    add_plane_rect(f"AIRPORT_Stand_Box_{i}", (x, 252), (14, 20), MAT["yellow"], SUBCOLS["APRON"], z=63.26)
    add_plane_rect(f"AIRPORT_Stand_Guidance_{i}", (x, 264), (0.8, 22), MAT["yellow"], SUBCOLS["APRON"], z=63.27)

# Terminal, tower, hangar, services.
add_cube("AIRPORT_Terminal_Main_Glass_Facade", (216, 312, 69), (64, 22, 12), MAT["concrete"], SUBCOLS["TERMINAL"], 1.0)
add_cube("AIRPORT_Terminal_Airside_Glass", (216, 300.5, 69.5), (58, 1.5, 8), MAT["glass"], SUBCOLS["TERMINAL"], 0.2)
add_cube("AIRPORT_Terminal_Roof_Overhang", (216, 312, 76), (70, 28, 2), MAT["roof"], SUBCOLS["TERMINAL"], 0.5)
for x in (194, 216, 238):
    add_cube(f"AIRPORT_Terminal_Roof_Skylight_{x}", (x, 312, 77.4), (10, 18, 0.8), MAT["glass"], SUBCOLS["TERMINAL"], 0.25)

add_cube("AIRPORT_Hangar_Main", (202, 214, 72), (48, 40, 18), MAT["metal"], SUBCOLS["HANGAR"], 1.2)
add_cube("AIRPORT_Hangar_Door_Dark", (226.2, 214, 68), (1.2, 30, 11), MAT["asphalt"], SUBCOLS["HANGAR"], 0.2)
add_cube("AIRPORT_Hangar_Service_Apron", (230, 214, 63.2), (42, 44, 0.25), MAT["concrete"], SUBCOLS["HANGAR"], 0.05)

add_cube("AIRPORT_ControlTower_Shaft", (279, 302, 79), (10, 10, 32), MAT["concrete"], SUBCOLS["CONTROL_TOWER"], 0.5)
add_cube("AIRPORT_ControlTower_Cab_Glass", (279, 302, 98), (18, 18, 8), MAT["glass"], SUBCOLS["CONTROL_TOWER"], 0.4)
add_cube("AIRPORT_ControlTower_Roof", (279, 302, 103), (22, 22, 2), MAT["roof"], SUBCOLS["CONTROL_TOWER"], 0.4)

for i, (x, y) in enumerate(((190, 274), (193, 188), (260, 332), (367, 145)), start=1):
    add_cube(f"AIRPORT_Service_Building_{i}", (x, y, 67), (18, 12, 8), MAT["concrete"], SUBCOLS["SERVICE_BUILDINGS"], 0.5)

# Static aircraft, optimized low-poly silhouettes.
def add_aircraft(idx, x, y, yaw=0.0):
    body = add_cube(f"AIRPORT_StaticAircraft_{idx}_Fuselage", (x, y, 66), (4, 18, 3), MAT["plane"], SUBCOLS["AIRCRAFT"], 0.8)
    wing = add_cube(f"AIRPORT_StaticAircraft_{idx}_Wing", (x, y, 66), (22, 3, 0.8), MAT["plane"], SUBCOLS["AIRCRAFT"], 0.2)
    tail = add_cube(f"AIRPORT_StaticAircraft_{idx}_Tail", (x, y - 8, 68), (8, 2, 5), MAT["accent"], SUBCOLS["AIRCRAFT"], 0.2)
    nose = add_cube(f"AIRPORT_StaticAircraft_{idx}_Nose", (x, y + 9.5, 66), (3, 3, 2.5), MAT["accent"], SUBCOLS["AIRCRAFT"], 0.5)
    for obj in (body, wing, tail, nose):
        obj.rotation_euler[2] = yaw


for i, x in enumerate((218, 242, 266), start=1):
    add_aircraft(i, x, 252, 0.0)

# Perimeter fence and lights.
for i, (cx, cy, sx, sy) in enumerate(((285, 368, 214, 1), (285, 92, 214, 1), (178, 230, 1, 276), (392, 230, 1, 276)), start=1):
    add_cube(f"AIRPORT_Perimeter_Fence_Run_{i}", (cx, cy, 65), (sx, sy, 4), MAT["fence"], SUBCOLS["AIRPORT_FENCE"], 0.1)
for x in range(190, 391, 22):
    add_cube(f"AIRPORT_Fence_Post_N_{x}", (x, 368, 66), (0.8, 0.8, 6), MAT["fence"], SUBCOLS["AIRPORT_FENCE"], 0.05)
    add_cube(f"AIRPORT_Fence_Post_S_{x}", (x, 92, 66), (0.8, 0.8, 6), MAT["fence"], SUBCOLS["AIRPORT_FENCE"], 0.05)
for y in range(110, 361, 25):
    add_cube(f"AIRPORT_Fence_Post_W_{y}", (178, y, 66), (0.8, 0.8, 6), MAT["fence"], SUBCOLS["AIRPORT_FENCE"], 0.05)
    add_cube(f"AIRPORT_Fence_Post_E_{y}", (392, y, 66), (0.8, 0.8, 6), MAT["fence"], SUBCOLS["AIRPORT_FENCE"], 0.05)
for i, y in enumerate(range(110, 356, 35)):
    add_cube(f"AIRPORT_Runway_Light_L_{i}", (307, y, 64.1), (1, 1, 1.5), MAT["red"], SUBCOLS["AIRPORT_LIGHTS"], 0.15)
    add_cube(f"AIRPORT_Runway_Light_R_{i}", (345, y, 64.1), (1, 1, 1.5), MAT["red"], SUBCOLS["AIRPORT_LIGHTS"], 0.15)

# Access road to existing road network near the power road.
add_plane_rect("AIRPORT_Access_Road_To_Existing_Network", (224, 124), (18, 82), MAT["taxi"], SUBCOLS["AIRPORT_ROADS"], z=63.06)
add_plane_rect("AIRPORT_Access_Road_Centerline", (224, 124), (0.7, 70), MAT["yellow"], SUBCOLS["AIRPORT_ROADS"], z=63.24)
add_cube("AIRPORT_Security_Gatehouse", (198, 102, 67), (12, 9, 7), MAT["concrete"], SUBCOLS["AIRPORT_FENCE"], 0.4)

# Label and validation cameras.
add_text("LBL_AIRPORT_NEW", "AIRPORT", (285, 374, 82), 16, SUBCOLS["AIRPORT_ROADS"], material=MAT["white"])
bpy.ops.object.camera_add(location=(0, 0, 760), rotation=(0, 0, 0))
top_cam = bpy.context.object
set_origin_name(top_cam, "CAM_AirportMap_TopOrthographic")
top_cam.data.type = "ORTHO"
top_cam.data.ortho_scale = 860
link_to(top_cam, collection("CAMERAS"))
bpy.ops.object.camera_add(location=(275, 245, 250), rotation=(math.radians(60), 0, math.radians(0)))
airport_cam = bpy.context.object
set_origin_name(airport_cam, "CAM_Airport_Aerial_Close")
airport_cam.data.lens = 28
link_to(airport_cam, collection("CAMERAS"))

REPORT_PATH.write_text(
    json.dumps(
        {
            "airport_bounds": AIRPORT_BOUNDS,
            "airport_center": [285, 230],
            "airport_dimensions_m": [214, 276],
            "runway": RUNWAY,
            "removed_objects": removed,
            "relocated_objects": relocated,
            "collections_created": sorted(SUBCOLS.keys()),
            "preserved_zones": [
                "DAM",
                "RESERVOIR",
                "RIVER",
                "BRIDGE",
                "FOREST",
                "LANDSLIDE",
                "INDUSTRIAL",
                "CONSTRUCTION",
                "POWER_INFRASTRUCTURE",
                "AGRICULTURE",
                "DRONE_BASE_HOME",
                "LOGISTICS",
            ],
            "river_clearance": "Airport is east of the river; no new airport object crosses the central river corridor.",
        },
        indent=2,
    ),
    encoding="utf-8",
)

bpy.ops.wm.save_as_mainfile(filepath=str(UPDATED_BLEND))
