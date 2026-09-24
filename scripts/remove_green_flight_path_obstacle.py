import json
import shutil
from pathlib import Path

import bpy


ROOT = Path(r"C:/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system")
SRC = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.blend"
BACKUP = ROOT / "uav-monitoring-compact-world-v6-roads-export-source_20260907_095003.before-green-flight-path-removal.blend"
REPORT = ROOT / "Forest3D" / "green_flight_path_obstacle_removal_report.json"

TARGET_OBJECTS = {
    "POLISH_River_Shrub_4_-1",
}


def main():
    if not BACKUP.exists():
        shutil.copy2(SRC, BACKUP)

    bpy.ops.wm.open_mainfile(filepath=str(SRC))

    removed = []
    missing = []
    for name in sorted(TARGET_OBJECTS):
        obj = bpy.data.objects.get(name)
        if obj is None:
            missing.append(name)
            continue
        removed.append(
            {
                "name": obj.name,
                "location": [round(v, 3) for v in obj.location],
                "collections": [c.name for c in obj.users_collection],
                "materials": [slot.material.name for slot in obj.material_slots if slot.material],
            }
        )
        bpy.data.objects.remove(obj, do_unlink=True)

    bpy.ops.wm.save_as_mainfile(filepath=str(SRC))

    REPORT.write_text(
        json.dumps(
            {
                "source": str(SRC),
                "backup": str(BACKUP),
                "removed": removed,
                "missing": missing,
            },
            indent=2,
        ),
        encoding="utf-8",
    )

    print(json.dumps({"removed": removed, "missing": missing}, indent=2))


if __name__ == "__main__":
    main()
