import json
import sys
from pathlib import Path

import bpy


def world_bounds(obj):
    if not obj.bound_box:
        return None
    corners = [obj.matrix_world @ __import__("mathutils").Vector(corner) for corner in obj.bound_box]
    return {
        "min": [round(min(c[i] for c in corners), 3) for i in range(3)],
        "max": [round(max(c[i] for c in corners), 3) for i in range(3)],
    }


objects = []
for obj in bpy.context.scene.objects:
    objects.append(
        {
            "name": obj.name,
            "type": obj.type,
            "collection": obj.users_collection[0].name if obj.users_collection else "",
            "location": [round(v, 3) for v in obj.location],
            "dimensions": [round(v, 3) for v in obj.dimensions],
            "bounds": world_bounds(obj),
        }
    )

summary = {
    "blend": bpy.data.filepath,
    "collections": sorted(col.name for col in bpy.data.collections),
    "objects": objects,
}

out_path = Path(sys.argv[-1])
out_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
