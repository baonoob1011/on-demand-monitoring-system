#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen
import xml.etree.ElementTree as ET


COORDINATE_SYSTEM = "LOCAL_SIMULATION_METERS_GAZEBO_XY"
WORLD_NAME = "forest_monitoring_compact"


def _fetch_sources(base_urls: list[str], timeout_s: float) -> list[dict]:
    last_error: Exception | None = None
    for base_url in base_urls:
        try:
            with urlopen(f"{base_url.rstrip('/')}/api/thermal-sources", timeout=timeout_s) as response:
                payload = json.loads(response.read().decode("utf-8"))
            data = payload.get("data", payload) if isinstance(payload, dict) else payload
            if not isinstance(data, list):
                raise ValueError("thermal source API did not return a list")
            return [item for item in data if isinstance(item, dict)]
        except (OSError, URLError, ValueError, json.JSONDecodeError) as exc:
            last_error = exc
    raise RuntimeError(f"thermal source API unavailable: {last_error}")


def _text(parent: ET.Element, name: str, value: object) -> ET.Element:
    child = ET.SubElement(parent, name)
    child.text = str(value)
    return child


def _thermal_visual(
    link: ET.Element,
    name: str,
    pose: tuple[float, float, float],
    geometry: tuple[str, tuple[float, ...]],
    temperature_c: float,
) -> None:
    visual = ET.SubElement(link, "visual", name=name)
    _text(visual, "pose", f"{pose[0]:.3f} {pose[1]:.3f} {pose[2]:.3f} 0 0 0")
    geometry_element = ET.SubElement(visual, "geometry")
    shape, dimensions = geometry
    shape_element = ET.SubElement(geometry_element, shape)
    if shape == "box":
        _text(shape_element, "size", " ".join(f"{value:.3f}" for value in dimensions))
    elif shape == "cylinder":
        _text(shape_element, "radius", f"{dimensions[0]:.3f}")
        _text(shape_element, "length", f"{dimensions[1]:.3f}")
    elif shape == "cone":
        _text(shape_element, "radius", f"{dimensions[0]:.3f}")
        _text(shape_element, "length", f"{dimensions[1]:.3f}")
    material = ET.SubElement(visual, "material")
    _text(material, "ambient", "0.32 0.055 0.01 1")
    _text(material, "diffuse", "0.62 0.10 0.01 1")
    plugin = ET.SubElement(
        visual,
        "plugin",
        filename="gz-sim-thermal-system",
        name="gz::sim::systems::Thermal",
    )
    _text(plugin, "temperature", f"{temperature_c + 273.15:.2f}")


def build_model(sources: list[dict]) -> ET.ElementTree:
    sdf = ET.Element("sdf", version="1.9")
    model = ET.SubElement(sdf, "model", name="compact_thermal_sources")
    _text(model, "static", "true")
    link = ET.SubElement(model, "link", name="thermal_signatures")

    for item in sources:
        if not item.get("active", True):
            continue
        if item.get("sourceWorld") != WORLD_NAME or item.get("coordinateSystem") != COORDINATE_SYSTEM:
            continue
        source_type = str(item.get("thermalType") or "").upper()
        if source_type == "AMBIENT":
            continue
        code = str(item.get("code") or "thermal_source").lower()
        x = float(item["centerXM"])
        y = float(item["centerYM"])
        radius = max(0.5, float(item["radiusM"]))
        temperature = float(item["temperatureC"])

        if source_type == "WARM_AREA":
            _thermal_visual(link, code, (x, y, 0.07), ("box", (radius * 2.0, radius * 1.2, 0.10)), temperature)
        elif source_type == "FIRE":
            _thermal_visual(link, f"{code}_base", (x, y, 0.55), ("box", (radius * 1.8, radius * 1.2, 1.1)), temperature)
            _thermal_visual(link, f"{code}_flame_a", (x - radius * 0.35, y, radius * 0.9), ("cone", (radius * 0.55, radius * 1.8)), temperature)
            _thermal_visual(link, f"{code}_flame_b", (x + radius * 0.35, y, radius * 0.7), ("cone", (radius * 0.42, radius * 1.4)), temperature)
        else:
            _thermal_visual(link, f"{code}_trunk", (x, y, radius * 0.3), ("cylinder", (radius * 0.22, radius * 0.6)), temperature)
            _thermal_visual(link, f"{code}_crown", (x, y, radius * 0.85), ("cone", (radius * 0.58, radius * 1.25)), temperature)

    ET.indent(sdf, space="  ")
    return ET.ElementTree(sdf)


def main() -> int:
    parser = argparse.ArgumentParser(description="Sync OMSS ThermalSource rows to native Gazebo heat geometry.")
    parser.add_argument("--backend-base-url", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=0.8)
    parser.add_argument("--strict", action="store_true", help="Raise backend/API errors instead of returning a soft failure.")
    args = parser.parse_args()

    try:
        sources = _fetch_sources(args.backend_base_url, args.timeout)
    except RuntimeError as exc:
        if args.strict:
            raise
        print(f"[THERMAL] Backend sync skipped: {exc}")
        return 2
    tree = build_model(sources)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    tree.write(args.output, encoding="utf-8", xml_declaration=True)
    print(f"[THERMAL] Synced {len(sources)} DB source rows to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
