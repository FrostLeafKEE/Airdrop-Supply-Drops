# Per-appearance crate and parachute resources (1.0.14+)

The supply type's `appearance` is now a namespaced resource ID. For example, a data-only integration can select `tacz_airdrop:military` without replacing the mineral or food models used by other supplies. The server does not need to know model paths or load client assets.

Both Forge 1.20.1 and Forge 1.21.1 support this contract. Use 1.0.14 or later on **both server and clients**: falling cargo now synchronizes its appearance ID. The old values `mineral` and `food` are still accepted, resolving to `airdrop_supply_drops:mineral` and `airdrop_supply_drops:food`. Schema version 1 remains valid. An unknown unqualified value, such as `military`, is rejected; write its full namespace.

## 1. Choose an appearance in server data

In `data/tacz_airdrop/airdrop_types/weapons.json`:

```json
{
  "schema_version": 1,
  "display_name": {"text": "Weapon supplies"},
  "weight": 1,
  "loot_table": "tacz_airdrop:airdrop/weapons",
  "appearance": "tacz_airdrop:military"
}
```

The integration must provide the referenced loot table. Add optional conditions, settings or required mods as described in [INTEGRATION.md](INTEGRATION.md). Loot, scheduling, smoke and collision remain controlled by the server. Multiple supply types may deliberately share one appearance ID, while different IDs select independent resources.

Existing falling cargo captures its appearance when prepared, saves it to NBT, synchronizes it to clients and transfers it to the landed block entity. A later datapack reload cannot retarget that cargo. Landed crates preserve their ID across world saves and send only that ID in visual update packets; inventory synchronization remains in the menu. Old saves without an ID recover mineral/food from their saved flag or block state.

## 2. Define the client appearance

Create `assets/tacz_airdrop/airdrop_appearances/military.json` in the integration mod or a client resource pack:

```json
{
  "format": 1,
  "crate_model": "tacz_airdrop:block/weapon_supply_crate",
  "parachute_model": "tacz_airdrop:entity/weapon_supply_parachute",
  "parachute_rig": "tacz_airdrop:parachute/weapon_supply_rig.json",
  "fallback": "mineral"
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `format` | Yes | Integer `1`. |
| `crate_model` | Yes | Namespaced baked model ID, without `models/` or `.json`. |
| `parachute_model` | No | Namespaced baked model ID. Omission uses the shared default canopy. |
| `parachute_rig` | No | Full namespaced JSON resource path. Omission uses the shared default rig. |
| `fallback` | No | `mineral` or `food` (their full built-in IDs also work). Defaults to mineral. Custom fallback chains are not supported. |

The appearance file's namespace and relative path determine its ID; for example `assets/my_pack/airdrop_appearances/crates/heavy.json` defines `my_pack:crates/heavy`. Its three resources may use other namespaces. Resource pack priority applies normally. Unknown fields, invalid IDs or unsupported formats discard that appearance definition and fall back to mineral. Missing definitions also fall back to mineral. A missing/invalid crate model uses the definition's fallback crate. A missing/invalid canopy uses the shared canopy and shared rig; if that canopy is also missing, the original procedural parachute stays visible. Invalid rig data uses the validated built-in rig. Invalid individual textures follow Minecraft's missing-texture behavior.

Assets are discovered and baked on each resource reload, including F3+T. Removing a pack removes its definitions and model selections; old atlas references are not retained.

## 3. Export models, textures and rigging

```text
assets/tacz_airdrop/
  airdrop_appearances/military.json
  models/block/weapon_supply_crate.json
  models/entity/weapon_supply_parachute.json
  parachute/weapon_supply_rig.json
  textures/block/weapon_supply_crate.png
  textures/entity/weapon_supply_parachute.png
```

Crates use normal baked block models: model units 0..16 span one block, with `(0,0,0)` at the bottom corner. Keep geometry inside that block; the physical hitbox and inventory size do not change. Landed crates use normal chunk rendering and delegate the model's render layer, preserving block lighting and ambient occlusion. Falling crates use the same appearance model with a double-sided cutout material. Use opaque/cutout materials for consistent results; translucent blending and animated lids are outside this contract. Texture references in model JSON omit `textures/` and `.png`. Use `"render_type": "minecraft:cutout"` if cutout pixels are needed.

Parachute dimensions, coordinate bounds, cord attachment points and opening transforms follow [PARACHUTE-PBR.md](PARACHUTE-PBR.md). Copy the existing rig JSON and change attachment points to match the exported canopy. A `.bbmodel` is an editable source project; runtime rendering needs a baked JSON model or a supported Forge model loader. `forge:obj` can be referenced in a model JSON using Forge's loader, but this release validates vanilla JSON geometry, not the partner's OBJ asset.

Textures under `textures/block/` are automatically available in the block atlas. Add non-block textures explicitly to `assets/minecraft/atlases/blocks.json` in the integration:

```json
{
  "sources": [
    {"type": "single", "resource": "tacz_airdrop:entity/weapon_supply_parachute"}
  ]
}
```

The cord texture uses a full PNG resource path and is rendered separately. Optional matching `_n.png` and `_s.png` files follow the existing LabPBR contract; shader support still depends on the player's shader pack.

## Independent example

`example_appearance_resourcepack` contains `example_airdrops:military` and `example_airdrops:medical`. They intentionally use simple contrasting demo crates, different canopy geometry and different rigs, reusing the mod's existing textures. They do not override the shared default assets and are not the TACZ collaboration models. Select either ID in a supply type, enable the pack, and spawn that type to test the complete delivery. The appearance pack alone does not create supply types or loot tables.

The older `example_resourcepack` still demonstrates a global replacement of the default canopy; it also includes the independent example definitions for reload regression checks. Use the independent pack above when testing that default deliveries remain unchanged.
