import argparse
import json
import math
from pathlib import Path

import numpy as np
import trimesh


ROOT = Path(__file__).resolve().parents[1]
SIMULATION_MAP_META = ROOT / "ondemandmonitoring" / "src" / "main" / "resources" / "static" / "simulation-viewer" / "simulation-map.json"
OUT = ROOT / "ondemandmonitoring" / "src" / "main" / "resources" / "planning" / "forest_monitoring_compact-planning-map.json"

TERRAIN_MODELS = ["compact_terrain"]
SURFACE_MODELS = [
    "compact_airport",
    "compact_bridges",
    "compact_home",
    "compact_highrise",
    "compact_zones",
    "compact_forest",
    "compact_environment_props",
    "compact_roads",
]
EXCLUDED_NODE_PREFIXES = ("Ref_",)


def load_mesh(model_name: str) -> trimesh.Trimesh | None:
    path = ROOT / "Forest3D" / "models" / model_name / "meshes" / f"{model_name}.glb"
    if not path.exists():
        return None

    loaded = trimesh.load(path, force="scene")
    if isinstance(loaded, trimesh.Scene):
        meshes = []
        for node_name in loaded.graph.nodes_geometry:
            if node_name.startswith(EXCLUDED_NODE_PREFIXES):
                continue
            transform, geometry_name = loaded.graph.get(node_name)
            node_mesh = loaded.geometry[geometry_name].copy()
            node_mesh.apply_transform(transform)
            meshes.append(node_mesh)
        mesh = trimesh.util.concatenate(meshes) if meshes else None
    else:
        mesh = loaded

    if mesh is None or mesh.is_empty:
        return None
    mesh.metadata["source_model"] = model_name
    return mesh


def combine_meshes(model_names: list[str]) -> tuple[trimesh.Trimesh | None, list[str]]:
    meshes = []
    loaded_names = []
    for name in model_names:
        mesh = load_mesh(name)
        if mesh is None:
            continue
        meshes.append(mesh)
        loaded_names.append(name)

    if not meshes:
        return None, loaded_names
    return trimesh.util.concatenate(meshes), loaded_names


def sample_top_z(mesh: trimesh.Trimesh | None, xs: np.ndarray, ys: np.ndarray, ray_top_z: float) -> list[float | None]:
    if mesh is None:
        return [None] * len(xs)

    origins = np.column_stack((xs, ys, np.full_like(xs, ray_top_z, dtype=float)))
    directions = np.tile(np.array([0.0, 0.0, -1.0]), (len(xs), 1))
    locations, index_ray, _ = mesh.ray.intersects_location(origins, directions, multiple_hits=True)

    top = np.full(len(xs), np.nan, dtype=float)
    for location, ray_index in zip(locations, index_ray):
        z = location[2]
        if math.isnan(top[ray_index]) or z > top[ray_index]:
            top[ray_index] = z

    return [None if math.isnan(value) else round(float(value), 3) for value in top]


def coord_values(min_value: float, max_value: float, resolution: float) -> list[float]:
    count = math.ceil((max_value - min_value) / resolution) + 1
    values = []
    for index in range(count):
        if index == count - 1:
            values.append(max_value)
        else:
            values.append(round(min_value + index * resolution, 6))
    return values


def safe_min(values: list[float | None]) -> float | None:
    known = [value for value in values if value is not None]
    return min(known) if known else None


def safe_max(values: list[float | None]) -> float | None:
    known = [value for value in values if value is not None]
    return max(known) if known else None


def make_sample_lookup(xs: list[float], ys: list[float], terrain: list[float | None], surface: list[float | None], width: int):
    points = {
        "home": (0.0, -280.0),
        "forest": (-200.15, 92.71),
        "airport": (-201.13, -245.30),
        "elevatedTerrain": (-245.0, 258.0),
        "staticObstacle": (235.0, -42.0),
    }
    samples = {}
    for label, (x, y) in points.items():
        col = min(range(len(xs)), key=lambda idx: abs(xs[idx] - x))
        row = min(range(len(ys)), key=lambda idx: abs(ys[idx] - y))
        index = row * width + col
        samples[label] = {
            "requestedX": x,
            "requestedY": y,
            "cellX": xs[col],
            "cellY": ys[row],
            "terrainElevationM": terrain[index],
            "surfaceElevationM": surface[index],
        }
    return samples


def generate(resolution: float, output: Path) -> dict:
    meta = json.loads(SIMULATION_MAP_META.read_text(encoding="utf-8"))
    min_x = float(meta["minX"])
    max_x = float(meta["maxX"])
    min_y = float(meta["minY"])
    max_y = float(meta["maxY"])
    xs = coord_values(min_x, max_x, resolution)
    ys = coord_values(min_y, max_y, resolution)
    width = len(xs)
    height = len(ys)
    grid_x, grid_y = np.meshgrid(np.array(xs), np.array(ys))
    flat_x = grid_x.reshape(-1)
    flat_y = grid_y.reshape(-1)

    terrain_mesh, terrain_sources = combine_meshes(TERRAIN_MODELS)
    surface_mesh, surface_sources = combine_meshes(SURFACE_MODELS)
    ray_top_z = 250.0
    terrain = sample_top_z(terrain_mesh, flat_x, flat_y, ray_top_z)
    surface_top = sample_top_z(surface_mesh, flat_x, flat_y, ray_top_z)

    obstacle_height = []
    surface = []
    obstacle_cells = 0
    for terrain_z, obstacle_top_z in zip(terrain, surface_top):
        if terrain_z is None and obstacle_top_z is None:
            obstacle_height.append(None)
            surface.append(None)
            continue

        if terrain_z is None:
            obstacle_height.append(None)
            surface.append(obstacle_top_z)
            if obstacle_top_z is not None:
                obstacle_cells += 1
            continue

        candidate_top_z = obstacle_top_z

        if candidate_top_z is None or candidate_top_z <= terrain_z + 0.25:
            obstacle_height.append(0.0)
            surface.append(terrain_z)
            continue

        height_m = round(candidate_top_z - terrain_z, 3)
        obstacle_height.append(height_m)
        surface.append(candidate_top_z)
        obstacle_cells += 1

    dataset = {
        "world": meta["worldName"],
        "coordinateSystem": meta["coordinateSystem"],
        "sourceWorld": meta["sourceWorld"],
        "sourceBlender": meta.get("sourceBlender"),
        "bounds": {
            "minX": min_x,
            "maxX": max_x,
            "minY": min_y,
            "maxY": max_y,
        },
        "resolutionM": resolution,
        "width": width,
        "height": height,
        "cellOrder": "row-major-y-increasing",
        "lookup": "nearest-cell",
        "terrainSources": terrain_sources,
        "surfaceSources": surface_sources,
        "excludedNodePrefixes": list(EXCLUDED_NODE_PREFIXES),
        "semantics": {
            "terrainElevationM": "Top Z intersection from compact_terrain GLB in Gazebo XY/Z coordinates.",
            "obstacleHeightM": "Top physical GLB surface Z minus terrain Z at the sampled XY coordinate when the difference is more than 0.25m; 0.0 when no higher physical surface exists; null if terrain is unknown.",
            "surfaceElevationM": "max terrain/static-surface Z at the sampled cell; null when no sampled source intersects.",
            "restricted": "Not stored in this file. Restricted zones remain sourced from Zone.restricted + Zone.polygon in the database.",
        },
        "stats": {
            "cellCount": width * height,
            "terrainKnownCells": sum(1 for value in terrain if value is not None),
            "terrainNoDataCells": sum(1 for value in terrain if value is None),
            "terrainMinM": safe_min(terrain),
            "terrainMaxM": safe_max(terrain),
            "surfaceMinM": safe_min(surface),
            "surfaceMaxM": safe_max(surface),
            "obstacleCells": obstacle_cells,
        },
        "samples": make_sample_lookup(xs, ys, terrain, surface, width),
        "terrainElevationM": terrain,
        "obstacleHeightM": obstacle_height,
        "surfaceElevationM": surface,
    }

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dataset, indent=2), encoding="utf-8")
    return dataset


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate backend planning grid from current Forest3D Gazebo GLB assets.")
    parser.add_argument("--resolution", type=float, default=1.0, help="Grid spacing in meters.")
    parser.add_argument("--output", type=Path, default=OUT)
    args = parser.parse_args()

    dataset = generate(args.resolution, args.output)
    stats = dataset["stats"]
    print(f"World: {dataset['world']}")
    print(f"Bounds: X {dataset['bounds']['minX']}..{dataset['bounds']['maxX']} | Y {dataset['bounds']['minY']}..{dataset['bounds']['maxY']}")
    print(f"Resolution: {dataset['resolutionM']} m")
    print(f"Grid: {dataset['width']} x {dataset['height']} = {stats['cellCount']} cells")
    print(f"Terrain: known={stats['terrainKnownCells']} noData={stats['terrainNoDataCells']} min={stats['terrainMinM']} max={stats['terrainMaxM']}")
    print(f"Surface: min={stats['surfaceMinM']} max={stats['surfaceMaxM']}")
    print(f"Obstacle cells: {stats['obstacleCells']}")
    print(f"Terrain sources: {', '.join(dataset['terrainSources'])}")
    print(f"Surface sources: {', '.join(dataset['surfaceSources'])}")
    print(f"Output: {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
