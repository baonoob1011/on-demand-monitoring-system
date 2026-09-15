from pathlib import Path

import bpy


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "tools" / "blender_scene_names.txt"

terms = ("forest", "remote", "target", "monitor", "radio", "tower", "landslide", "agri", "log", "ind", "airport", "home")
lines = ["COLLECTIONS"]
for collection in sorted(bpy.data.collections, key=lambda item: item.name.lower()):
    if any(term in collection.name.lower() for term in terms):
        lines.append(collection.name)

lines.append("")
lines.append("OBJECTS")
for obj in sorted(bpy.context.scene.objects, key=lambda item: item.name.lower()):
    text = " ".join([obj.name, *[collection.name for collection in obj.users_collection]]).lower()
    if any(term in text for term in terms):
        lines.append(f"{obj.name} | {', '.join(collection.name for collection in obj.users_collection)}")

OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"Wrote {OUT}")
