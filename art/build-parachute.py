"""Rebuild the original canopy's Java model and an editable Blockbench project.
The collaboration models are deliberately not used by this mod.
"""
from pathlib import Path
import base64
import json
import uuid

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/airdrop_supply_drops"


def canopy(panels):
    model = {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"particle": "airdrop_supply_drops:entity/canopy_ivory",
                          "ivory": "airdrop_supply_drops:entity/canopy_ivory",
                          "red": "airdrop_supply_drops:entity/canopy_red"}, "elements": []}
    for i in range(panels):
        rise = (panels // 2 - abs(i - panels // 2)) * (.2 if panels == 5 else .12)
        model["elements"].append({"name": f"canopy_panel_{i}",
            "from": [round(i * 16 / panels, 6), 0, 0],
            "to": [round((i + 1) * 16 / panels, 6), round((.12 + rise) * 16 / 3.8, 6), 16],
            "shade": False, "faces": {face: {"uv": [0, 0, 16, 16],
                "texture": "#red" if i == panels // 2 else "#ivory"}
                for face in ["north", "south", "east", "west", "up", "down"]}})
    return model


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def project(model):
    textures = []
    for name in ["ivory", "red"]:
        data = (ASSETS / f"textures/entity/canopy_{name}.png").read_bytes()
        textures.append({"path": "", "name": f"canopy_{name}.png", "namespace": "airdrop_supply_drops",
                         "folder": "entity", "id": str(len(textures)), "width": 64, "height": 64,
                         "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"airdrop/{name}")),
                         "source": "data:image/png;base64," + base64.b64encode(data).decode()})
    elements = []
    for cube in model["elements"]:
        elements.append({"name": cube["name"], "from": cube["from"], "to": cube["to"],
                         "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"airdrop/{cube['name']}")),
                         "type": "cube", "box_uv": False, "origin": [8, 0, 8],
                         "faces": {face: {"uv": [0, 0, 16, 16], "texture": 1 if f["texture"] == "#red" else 0}
                                   for face, f in cube["faces"].items()}})
    return {"meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
            "name": "parachute_canopy", "model_identifier": "parachute_canopy", "resolution": {"width": 16, "height": 16},
            "elements": elements, "outliner": [e["uuid"] for e in elements], "textures": textures}


def main():
    default = canopy(5)
    write_json(ASSETS / "models/entity/parachute_canopy.json", default)
    write_json(ROOT / "art/source/parachute_canopy.bbmodel", project(default))
    example = ROOT / "example_resourcepack"
    write_json(example / "pack.mcmeta", {"pack": {"pack_format": 15, "supported_formats": {"min_inclusive": 15, "max_inclusive": 34},
               "description": "Airdrop example: nine-panel canopy and eight suspension cords"}})
    write_json(example / "assets/airdrop_supply_drops/models/entity/parachute_canopy.json", canopy(9))
    rig = json.loads((ASSETS / "parachute/rigging.json").read_bytes())
    rig["open_width"] = 4.4
    for px, pz in [(0, 8), (16, 8), (8, 0), (8, 16)]:
        rig["cords"].append({"crate": [(px - 8) / 8 * .42, 1, (pz - 8) / 8 * .42], "canopy": [px, 0, pz]})
    write_json(example / "assets/airdrop_supply_drops/parachute/rigging.json", rig)
    print("Original canopy, embedded-texture Blockbench project and separate example resource pack built")


if __name__ == "__main__":
    main()
