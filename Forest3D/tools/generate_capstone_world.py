#!/usr/bin/env python3
"""Generate a capstone-ready Gazebo Sim forest monitoring world with realistic assets."""

from __future__ import annotations
import math
import random
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "worlds" / "forest_monitoring.sdf"
RNG = random.Random(20260905)

def sub(parent: ET.Element, tag: str, text: str | None = None, **attrs: str) -> ET.Element:
    elem = ET.SubElement(parent, tag, attrs)
    if text is not None:
        elem.text = text
    return elem

def material(visual: ET.Element, ambient: str, diffuse: str, roughness: str = "0.8", metalness: str = "0.0") -> None:
    mat = sub(visual, "material")
    sub(mat, "ambient", ambient)
    sub(mat, "diffuse", diffuse)
    sub(mat, "specular", "0.1 0.1 0.1 1")
    pbr = sub(mat, "pbr")
    metal = sub(pbr, "metal")
    sub(metal, "roughness", roughness)
    sub(metal, "metalness", metalness)

def link_model(world: ET.Element, name: str, pose: str, static: bool = True) -> tuple[ET.Element, ET.Element]:
    model = sub(world, "model", name=name)
    sub(model, "static", str(static).lower())
    sub(model, "pose", pose)
    link = sub(model, "link", name="link")
    return model, link

def box(world: ET.Element, name: str, pose: str, size: str, ambient: str, diffuse: str, collision: bool = True, roughness: str = "0.8") -> None:
    _, link = link_model(world, name, pose)
    if collision:
        col = sub(link, "collision", name="collision")
        geom = sub(col, "geometry")
        b = sub(geom, "box")
        sub(b, "size", size)
    vis = sub(link, "visual", name="visual")
    geom = sub(vis, "geometry")
    b = sub(geom, "box")
    sub(b, "size", size)
    material(vis, ambient, diffuse, roughness)

def cylinder(world: ET.Element, name: str, pose: str, radius: float, length: float, ambient: str, diffuse: str, collision: bool = True) -> None:
    _, link = link_model(world, name, pose)
    if collision:
        col = sub(link, "collision", name="collision")
        geom = sub(col, "geometry")
        c = sub(geom, "cylinder")
        sub(c, "radius", f"{radius:.3f}")
        sub(c, "length", f"{length:.3f}")
    vis = sub(link, "visual", name="visual")
    geom = sub(vis, "geometry")
    c = sub(geom, "cylinder")
    sub(c, "radius", f"{radius:.3f}")
    sub(c, "length", f"{length:.3f}")
    material(vis, ambient, diffuse)

def sphere(world: ET.Element, name: str, pose: str, radius: float, ambient: str, diffuse: str, collision: bool = True) -> None:
    _, link = link_model(world, name, pose)
    if collision:
        col = sub(link, "collision", name="collision")
        geom = sub(col, "geometry")
        s = sub(geom, "sphere")
        sub(s, "radius", f"{radius:.3f}")
    vis = sub(link, "visual", name="visual")
    geom = sub(vis, "geometry")
    s = sub(geom, "sphere")
    sub(s, "radius", f"{radius:.3f}")
    material(vis, ambient, diffuse)

def include_model(world: ET.Element, name: str, pose: str, uri: str) -> None:
    inc = sub(world, "include")
    sub(inc, "name", name)
    sub(inc, "pose", pose)
    sub(inc, "uri", uri)

def visual_tree(world: ET.Element, name: str, pose: str, height: float, canopy_radius: float) -> None:
    _, link = link_model(world, name, pose)

    trunk = sub(link, "visual", name="trunk")
    sub(trunk, "pose", f"0 0 {height / 2:.2f} 0 0 0")
    trunk_geom = sub(trunk, "geometry")
    trunk_cyl = sub(trunk_geom, "cylinder")
    sub(trunk_cyl, "radius", f"{max(0.18, canopy_radius * 0.12):.2f}")
    sub(trunk_cyl, "length", f"{height:.2f}")
    material(trunk, "0.18 0.11 0.06 1", "0.24 0.15 0.08 1", "0.9")

    for idx, (dx, dy, dz, scale) in enumerate(
        (
            (0.0, 0.0, height * 0.88, 1.00),
            (canopy_radius * 0.42, canopy_radius * 0.12, height * 0.78, 0.82),
            (-canopy_radius * 0.38, canopy_radius * 0.08, height * 0.76, 0.78),
            (canopy_radius * 0.04, -canopy_radius * 0.42, height * 0.74, 0.76),
        )
    ):
        canopy = sub(link, "visual", name=f"canopy_{idx}")
        sub(canopy, "pose", f"{dx:.2f} {dy:.2f} {dz:.2f} 0 0 0")
        canopy_geom = sub(canopy, "geometry")
        canopy_sphere = sub(canopy_geom, "sphere")
        sub(canopy_sphere, "radius", f"{canopy_radius * scale:.2f}")
        material(canopy, "0.03 0.16 0.05 1", "0.05 0.28 0.08 1", "1.0")

def simple_vehicle(world: ET.Element, name: str, pose: str, body_color: str = "0.12 0.18 0.24 1") -> None:
    model, link = link_model(world, name, pose)
    body_col = sub(link, "collision", name="body_collision")
    body_geom = sub(body_col, "geometry")
    body_box = sub(body_geom, "box")
    sub(body_box, "size", "4.8 2.2 1.2")
    body_vis = sub(link, "visual", name="body")
    sub(body_vis, "pose", "0 0 0.75 0 0 0")
    body_geom = sub(body_vis, "geometry")
    body_box = sub(body_geom, "box")
    sub(body_box, "size", "4.8 2.2 1.2")
    material(body_vis, body_color, body_color, "0.45")

    cabin_vis = sub(link, "visual", name="cabin")
    sub(cabin_vis, "pose", "-0.35 0 1.55 0 0 0")
    cabin_geom = sub(cabin_vis, "geometry")
    cabin_box = sub(cabin_geom, "box")
    sub(cabin_box, "size", "2.3 1.9 0.9")
    material(cabin_vis, "0.08 0.12 0.14 1", "0.10 0.18 0.22 1", "0.25")

    for idx, x in enumerate((-1.55, 1.55)):
        for idy, y in enumerate((-1.2, 1.2)):
            wheel = sub(link, "visual", name=f"wheel_{idx}_{idy}")
            sub(wheel, "pose", f"{x} {y} 0.35 1.5708 0 0")
            wheel_geom = sub(wheel, "geometry")
            wheel_cyl = sub(wheel_geom, "cylinder")
            sub(wheel_cyl, "radius", "0.42")
            sub(wheel_cyl, "length", "0.28")
            material(wheel, "0.02 0.02 0.02 1", "0.03 0.03 0.03 1", "0.7")

def radio_tower(world: ET.Element, name: str, pose: str) -> None:
    model, link = link_model(world, name, pose)
    mast_col = sub(link, "collision", name="mast_collision")
    mast_geom = sub(mast_col, "geometry")
    mast_cyl = sub(mast_geom, "cylinder")
    sub(mast_cyl, "radius", "0.35")
    sub(mast_cyl, "length", "22")

    mast_vis = sub(link, "visual", name="mast")
    sub(mast_vis, "pose", "0 0 11 0 0 0")
    mast_geom = sub(mast_vis, "geometry")
    mast_cyl = sub(mast_geom, "cylinder")
    sub(mast_cyl, "radius", "0.35")
    sub(mast_cyl, "length", "22")
    material(mast_vis, "0.45 0.48 0.50 1", "0.58 0.60 0.62 1", "0.35", "0.0")

    for idx, height in enumerate((6, 12, 18)):
        deck = sub(link, "visual", name=f"antenna_bar_{idx}")
        sub(deck, "pose", f"0 0 {height} 0 1.5708 0")
        deck_geom = sub(deck, "geometry")
        deck_cyl = sub(deck_geom, "cylinder")
        sub(deck_cyl, "radius", "0.06")
        sub(deck_cyl, "length", "7")
        material(deck, "0.82 0.82 0.78 1", "0.92 0.92 0.86 1", "0.4")

    beacon = sub(link, "visual", name="beacon")
    sub(beacon, "pose", "0 0 22.6 0 0 0")
    beacon_geom = sub(beacon, "geometry")
    beacon_sphere = sub(beacon_geom, "sphere")
    sub(beacon_sphere, "radius", "0.45")
    material(beacon, "0.9 0.06 0.04 1", "1.0 0.08 0.05 1", "0.2")

def hazard_barrel(world: ET.Element, name: str, pose: str) -> None:
    cylinder(world, name, pose, 0.35, 0.9, "0.9 0.22 0.04 1", "1.0 0.32 0.06 1")

def hazard_cone(world: ET.Element, name: str, pose: str) -> None:
    cylinder(world, name, pose, 0.28, 0.65, "0.95 0.32 0.03 1", "1.0 0.45 0.06 1")

def add_base(world: ET.Element) -> None:
    cylinder(world, "drone_landing_pad", "0 0 0.035 0 0 0", 6.0, 0.07, "0.05 0.05 0.05 1", "0.08 0.08 0.08 1")
    cylinder(world, "landing_pad_inner_ring", "0 0 0.085 0 0 0", 4.0, 0.025, "0.9 0.9 0.82 1", "0.95 0.95 0.84 1", False)
    box(world, "landing_pad_h_mark_v", "0 0 0.11 0 0 0", "0.7 4.6 0.025", "0.04 0.04 0.04 1", "0.04 0.04 0.04 1", False, roughness="0.8")
    box(world, "landing_pad_h_mark_l", "-1.25 0 0.115 0 0 0", "0.7 2.6 0.025", "0.04 0.04 0.04 1", "0.04 0.04 0.04 1", False, roughness="0.8")
    box(world, "landing_pad_h_mark_r", "1.25 0 0.115 0 0 0", "0.7 2.6 0.025", "0.04 0.04 0.04 1", "0.04 0.04 0.04 1", False, roughness="0.8")
    box(world, "control_cabin", "-12 -12 1.6 0 0 0.25", "6 4 3.2", "0.8 0.8 0.8 1", "0.9 0.9 0.9 1", roughness="0.2")
    simple_vehicle(world, "base_suv", "-18 -10 0.05 0 0 0.8", "0.10 0.16 0.22 1")

def add_trail(world: ET.Element) -> None:
    segments = [
        ("trail_base", 0, 27, 0.01, 6.0, 30, 0.08),
        ("trail_curve_1", 12, 45, 0.012, 5.2, 42, -0.34),
        ("trail_curve_2", 34, 78, 0.015, 4.8, 46, 0.18),
        ("trail_clearing_link", 59, 108, 0.018, 4.5, 45, -0.42),
        ("trail_target_link", 85, 135, 0.02, 4.3, 53, 0.22),
    ]
    for name, x, y, z, width, length, yaw in segments:
        box(world, name, f"{x} {y} {z} 0 0 {yaw}", f"{width} {length} 0.03", "0.36 0.25 0.14 1", "0.46 0.32 0.18 1", False, roughness="1.0")

def add_monitoring_target(world: ET.Element) -> None:
    x, y = 115, 155
    radio_tower(world, "monitoring_radio_tower", f"{x} {y} 0.0 0 0 0")
    box(world, "equipment_container", f"{x+6} {y-4} 1.3 0 0 0.3", "6 2.5 2.6", "0.2 0.3 0.6 1", "0.25 0.35 0.65 1", roughness="0.4")
    simple_vehicle(world, "target_pickup", f"{x-5} {y+10} 0.0 0 0 -0.5", "0.30 0.34 0.18 1")

def add_emergency_area(world: ET.Element) -> None:
    box(world, "burnt_ground_patch", "130 55 0.01 0 0 -0.22", "30 20 0.02", "0.1 0.1 0.1 1", "0.12 0.12 0.12 1", False, roughness="1.0")
    simple_vehicle(world, "damaged_vehicle", "132 55 0.05 0 0 -0.4", "0.18 0.13 0.11 1")
    for i in range(4):
        hazard_barrel(world, f"hazard_barrel_{i}", f"{125+i*2} {50+i} 0.45 0 0 0")
    for i in range(3):
        hazard_cone(world, f"hazard_cone_{i}", f"{138-i*2} {60+i} 0.325 0 0 0")

def add_vegetation(world: ET.Element) -> None:
    excluded = [(0, 0, 32), (115, 155, 22), (130, 55, 24)]
    trail_points = [(0, 18), (12, 45), (34, 78), (59, 108), (85, 135)]

    def valid(x: float, y: float) -> bool:
        if any(math.hypot(x - ex, y - ey) < r for ex, ey, r in excluded):
            return False
        if any(math.hypot(x - tx, y - ty) < 9 for tx, ty in trail_points):
            return False
        return True

    placed = []
    clusters = [(-80, 80, 45), (-60, 140, 40), (50, 140, 35), (80, -70, 40), (-100, -60, 35), (140, -10, 35)]
    idx = 0
    for cx, cy, radius in clusters:
        for _ in range(12):
            for _attempt in range(50):
                angle = RNG.uniform(0, math.tau)
                dist = radius * math.sqrt(RNG.random())
                x = cx + math.cos(angle) * dist
                y = cy + math.sin(angle) * dist
                if valid(x, y) and all(math.hypot(x - px, y - py) > 9 for px, py in placed):
                    placed.append((x, y))
                    yaw = RNG.uniform(0, math.tau)
                    height = RNG.uniform(7.0, 13.0)
                    canopy_radius = RNG.uniform(2.4, 4.2)
                    visual_tree(world, f"tree_{idx}", f"{x:.2f} {y:.2f} 0 0 0 {yaw:.2f}", height, canopy_radius)
                    idx += 1
                    break
    
    for i, (x, y) in enumerate(placed[::3]):
        sphere(world, f"bush_{i}", f"{x + RNG.uniform(-6, 6):.2f} {y + RNG.uniform(-6, 6):.2f} 0.65 0 0 0", RNG.uniform(0.8, 1.5), "0.04 0.20 0.07 1", "0.07 0.32 0.11 1", False)

def main() -> None:
    sdf = ET.Element("sdf", version="1.9")
    world = sub(sdf, "world", name="forest_monitoring")
    sub(world, "physics", type="ode")
    physics = world.find("physics")
    sub(physics, "max_step_size", "0.004")
    sub(physics, "real_time_factor", "1.0")
    sub(physics, "real_time_update_rate", "250")
    sub(world, "gravity", "0 0 -9.8")
    sub(world, "magnetic_field", "6e-06 2.3e-05 -4.2e-05")
    sub(world, "atmosphere", type="adiabatic")
    
    scene = sub(world, "scene")
    sub(scene, "grid", "false")
    sub(scene, "ambient", "0.6 0.6 0.6 1")
    sub(scene, "background", "0.7 0.8 0.95 1")
    sub(scene, "shadows", "true")
    sky = sub(scene, "sky")
    sub(sky, "clouds")
    sub(sky, "time", "11.0")

    light = sub(world, "light", name="sunUTC", type="directional")
    sub(light, "pose", "0 0 500 0 -0.5 -0.5")
    sub(light, "cast_shadows", "true")
    sub(light, "intensity", "1.2")
    sub(light, "direction", "-0.5 0.5 -0.8")
    sub(light, "diffuse", "0.95 0.93 0.88 1")
    sub(light, "specular", "0.3 0.3 0.25 1")

    box(world, "grassland_base", "0 0 -0.05 0 0 0", "400 400 0.1", "0.22 0.32 0.18 1", "0.25 0.38 0.21 1", roughness="0.9")

    add_trail(world)
    add_base(world)
    add_monitoring_target(world)
    add_emergency_area(world)
    add_vegetation(world)

    coords = sub(world, "spherical_coordinates")
    sub(coords, "surface_model", "EARTH_WGS84")
    sub(coords, "world_frame_orientation", "ENU")
    sub(coords, "latitude_deg", "47.397971057728974")
    sub(coords, "longitude_deg", "8.546163739800146")
    sub(coords, "elevation", "0")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    tree_doc = ET.ElementTree(sdf)
    ET.indent(tree_doc, space="  ")
    tree_doc.write(OUT, encoding="utf-8", xml_declaration=True)
    print(OUT)

if __name__ == "__main__":
    main()
