"""Rebuild bundled LabPBR 1.3 data maps from the mod's existing albedo art.

Requires Python, Pillow and NumPy. No creative/source albedo images are changed.
Normal B is ambient occlusion (not normal Z); specular A is zero emission.
"""
from pathlib import Path
import hashlib
import json
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/airdrop_supply_drops/textures"
PROFILES = {
    "crate_wood": ("wood", .82, 18, .09),
    "crate_lid": ("wood", .82, 18, .07),
    "crate_metal": ("painted_iron", .48, 0, .045),
    "crate_latch": ("iron", .28, 0, .045),
    "crate_food": ("painted_iron", .68, 0, .025),
    "crate_mineral": ("painted_iron", .68, 0, .025),
    "aircraft_body": ("painted_iron", .65, 0, .035),
    "aircraft_frame": ("painted_iron", .55, 0, .035),
    "aircraft_glass": ("glass", .08, 0, .003),
    "aircraft_rubber": ("rubber", .95, 4, .025),
    "canopy_ivory": ("cloth", .90, 105, .018),
    "canopy_red": ("cloth", .90, 105, .018),
    "signal_body": ("painted_iron", .68, 0, .028),
    "signal_label": ("painted_iron", .68, 0, .028),
    "signal_metal": ("iron", .40, 0, .022),
    "signal_dark": ("rubber", .98, 0, .015),
}


def generate(path, material, roughness, porosity_sss, relief):
    albedo = Image.open(path).convert("RGBA")
    pixels = np.asarray(albedo).astype(np.float32) / 255
    rgb = pixels[:, :, :3]
    luma = rgb @ np.array([.2126, .7152, .0722], dtype=np.float32)
    height, width = luma.shape
    y, x = np.mgrid[:height, :width]
    # Low relief: surface grain/seams, never a large extrusion from painted shadows.
    blurred = np.asarray(Image.fromarray(np.uint8(luma * 255)).filter(ImageFilter.GaussianBlur(.65))) / 255
    detail = luma - blurred
    surface = np.clip(.965 + relief * ((blurred - .5) + detail * 1.4), .90, 1)
    if material == "cloth":
        # A subtle woven structure distinct from metal/wood; seams follow the art.
        surface += ((x % 2) * 2 - 1) * .003 + ((y % 2) * 2 - 1) * .003
    surface = np.clip(surface, .90, 1)
    # Central differences wrap because these entity materials tile beyond UV 0..1.
    dx = (np.roll(surface, -1, 1) - np.roll(surface, 1, 1)) * width * .16
    dy = (np.roll(surface, -1, 0) - np.roll(surface, 1, 0)) * height * .16
    normal = np.stack((-dx, -dy, np.ones_like(dx)), axis=-1)
    normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
    normal_map = np.zeros((height, width, 4), dtype=np.uint8)
    normal_map[:, :, :2] = np.uint8(np.rint((normal[:, :, :2] * .5 + .5) * 255))
    normal_map[:, :, 2] = np.uint8(np.rint(np.clip(1 - np.maximum(.40 - blurred, 0) * .08, .94, 1) * 255))
    normal_map[:, :, 3] = np.uint8(np.rint(surface * 255))

    specular = np.zeros_like(normal_map)
    surface_roughness = np.clip(roughness - detail * .12, .04, .99)
    # LabPBR R encodes perceptual smoothness, not linear roughness.
    specular[:, :, 0] = np.uint8(np.rint((1 - np.sqrt(surface_roughness)) * 255))
    specular[:, :, 1] = 10  # dielectric F0 ~= 0.04; paint stays dielectric
    specular[:, :, 2] = porosity_sss
    if material == "iron":
        specular[:, :, 1] = 230
    elif material == "painted_iron":
        # Neutral exposed scratches/rivets; coloured coating and stencils stay paint.
        saturation = rgb.max(axis=2) - rgb.min(axis=2)
        exposed = (saturation < .065) & (luma > .24)
        specular[exposed, 1] = 230
        specular[exposed, 0] = 125
    elif material == "rubber":
        specular[:, :, 1] = 8
    # Alpha deliberately remains zero. Save lossless RGBA with RGB intact at A=0.
    # These are data textures, not premultiplied colour images.
    records = {}
    for suffix, data in [("_n", normal_map), ("_s", specular)]:
        output = path.with_stem(path.stem + suffix)
        Image.fromarray(data).save(output)
        assert np.array_equal(np.asarray(Image.open(output)), data), output
        records[suffix] = hashlib.sha256(output.read_bytes()).hexdigest()
    return {"material": material, "roughness": roughness, "relief": relief,
            "porosity_or_sss": porosity_sss, "size": [width, height],
            "albedo_sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "maps_sha256": records}


def main():
    manifest = {"format": "lab-pbr/1.3", "emission": 0, "textures": {}}
    for folder in ["block", "entity", "item"]:
        for path in sorted((TEXTURES / folder).glob("*.png")):
            if path.stem in PROFILES:
                manifest["textures"][f"{folder}/{path.name}"] = generate(path, *PROFILES[path.stem])
    assert len(manifest["textures"]) == len(PROFILES)
    (ROOT / "art/pbr-materials.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Built {len(PROFILES) * 2} LabPBR maps; albedo unchanged; lossless channels verified")


if __name__ == "__main__":
    main()
