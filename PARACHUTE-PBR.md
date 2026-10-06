# Parachute models and PBR materials

## Independent appearances (1.0.14+)

Each supply type can now select a namespaced `appearance` ID that resolves a crate model, canopy model and suspension rig. See [MODEL-APPEARANCES.md](MODEL-APPEARANCES.md) for the per-appearance contract. The paths below still replace the shared defaults.

## Replace the parachute

Since 1.0.12, a client resource pack can replace both the canopy geometry and suspension rig without changing server gameplay. Open `art/source/parachute_canopy.bbmodel` in Blockbench using the Java Block/Item format. It contains embedded cloth textures. Export a Java model to:

`assets/airdrop_supply_drops/models/entity/parachute_canopy.json`

This is a standard baked model: vanilla `parent`, `textures`, `elements`, face UVs and supported element rotations work. Forge model loaders such as `forge:obj` may also be used through the normal Forge baking pipeline. A Generic Model `.bbmodel` is an editor project and cannot be copied into the runtime JSON path. Export it using a supported Forge loader instead. OBJ rendering has not been separately verified.

The model's origin is `(8, 0, 8)` in model units. X/Z `0..16` span the configured canopy width; positive Y points up. The default width is 3.8 blocks. One Y model unit becomes `open_width / 16` blocks at full deployment. Keep model geometry within vanilla's coordinate bounds `-16..32`; the renderer's visibility bounds cover that range. The canopy is rendered on both sides using a cutout material and the block texture atlas. Do not set face `cullface` for a free-standing canopy. Translucent material blending is not provided.

Textures in `textures/block/` join the block atlas automatically. For a new texture in another directory, add a `single` atlas source in your pack's `assets/minecraft/atlases/blocks.json`:

```json
{"sources": [{"type": "single", "resource": "yourpack:entity/custom_canopy"}]}
```

The original cloth textures are registered by the mod. References omit `textures/` and `.png` in model JSON, e.g. `airdrop_supply_drops:entity/canopy_ivory`.

Replace the suspension rig at:

`assets/airdrop_supply_drops/parachute/rigging.json`

Copy the mod's default JSON as a starting point. The schema is versioned with `"format": 1`:

| Field | Meaning and permitted range |
| --- | --- |
| `open_width` | Deployed canopy width in blocks, 0.1–16. |
| `closed_width` | Initial width, 0.05–`open_width`. |
| `open_height` | Canopy origin above the crate bottom, 1–16 blocks. |
| `closed_height` | Initial origin height, 1–`open_height`. |
| `closed_vertical_scale` | Initial canopy vertical scale, 0.01–1. |
| `cord_width` | Suspension cord thickness, 0.005–0.25 blocks. |
| `cord_texture` | Full resource ID including `textures/` and `.png`. |
| `cords` | 1–64 entries, each with numeric 3D `crate` and `canopy` points. |

`crate` coordinates are in blocks relative to the crate's bottom centre: the default top corners are `(±0.42, 1, ±0.42)`; coordinates must be within -1..2. `canopy` coordinates use the model units above, within -16..32. Cord endpoints follow the same opening scale and sway as the canopy, so the attachments stay connected. All values must be finite numbers. Opening time, gravity, descent speed, landing and loot stay server-controlled; the resource pack changes only appearance.

The rig and baked model are refreshed on F3+T or when changing resource packs. Invalid rig files warn in the client log and restore the complete default rig. Missing or malformed canopy models use the original Java canopy fallback. Invalid texture references follow Minecraft's usual missing-texture behaviour. The server `validate` command checks datapacks, not these client assets.

The separate `example_resourcepack` uses nine canopy panels, eight cords and a 4.4-block width. Enable it to test replacement, then disable it to restore the default. It is a demonstration, not the TACZ collaboration asset set.

## Bundled LabPBR 1.3

The mod ships normal/height/AO (`_n.png`) and material (`_s.png`) maps for all twelve existing crate, aircraft and parachute textures. They are included in the base resources, so no optional pack or mod setting is required. Enabling a compatible shader pack automatically makes the maps available to that shader. Without shaders, the original albedo textures render normally.

Actual material effects depend on the shader's LabPBR and entity-PBR support and its normal/specular settings. A shader can support block PBR while ignoring entity PBR; in that case aircraft and cords retain ordinary shading. The mod neither installs shaders nor changes the user's shader settings. Shader loaders may have different support across Forge/Minecraft versions.

Wood has subtle grain relief and low gloss; labels and aircraft coating remain dielectric paint, with metallic material only at exposed neutral scratches/rivets. The latch uses iron, cockpit glass is smooth dielectric, rubber stays rough, and the canopy uses woven-cloth relief and modest subsurface scattering. Lights keep their existing emissive rendering; non-light surfaces have zero PBR emission. Height relief is deliberately shallow to avoid apparent floating panels.

Channel encoding follows the [LabPBR 1.3 standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard):

| Map | R | G | B | A |
| --- | --- | --- | --- | --- |
| `_n` | Tangent normal X | DirectX normal Y | Ambient occlusion | Height |
| `_s` | Perceptual smoothness | Dielectric F0 or metal ID | Porosity or subsurface scattering | Emission (zero) |

The format declaration is `assets/minecraft/optifine/texture.properties` with `format=lab-pbr/1.3`, following the [texture-properties contract](https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/texture.properties). This format declaration is global in shader loaders; a higher-priority resource pack can override it. Avoid mixing oldPBR and LabPBR assets without checking the shader's interpretation.

Rebuild maps with Python, Pillow and NumPy:

```sh
python art/build-pbr.py
python art/build-parachute.py
```

`art/pbr-materials.json` records profiles, sizes and source/map hashes. Generated RGBA maps preserve RGB values even when specular alpha is zero; colour-image premultiplication or transparent-pixel cleanup would corrupt them. Resource packs replacing albedo should replace the matching PBR maps as well. Build and client resource checks do not establish the appearance under a particular shader pack; that needs a shader-enabled gameplay test.
