# Modpack integration

Airdrop: Supply Drops supports server configuration, datapacks, and resource packs. This guide describes **Minecraft 1.21.1**. Use the guide and example from the branch matching your game version.

## Install the example

Copy `example_datapack` into `<world>/datapacks/`. Its root must contain `pack.mcmeta` and `data`. This version uses datapack format **48**. The example adds `example_airdrops:medical` and `example_airdrops:survival`; it is not bundled into the mod JAR.

```text
/reload
/airdrop_supply_drops validate
/airdrop_supply_drops list
/airdrop_supply_drops crate example_airdrops:medical
/airdrop_supply_drops spawn example_airdrops:survival
```

`crate` places a crate two blocks ahead; `spawn` starts an aircraft event and waits for any current aircraft to depart. Both player-only administrator commands bypass automatic conditions and the global dimension whitelist. Use automatic scheduling to test environmental conditions.

## Define a supply type

Create `data/<namespace>/airdrop_types/<name>.json`. Its ID is `<namespace>:<name>`:

```json
{
  "schema_version": 1,
  "display_name": {"text": "Medical supplies"},
  "weight": 1,
  "loot_table": "example_airdrops:airdrop/medical",
  "appearance": "food",
  "required_mods": [],
  "conditions": {
    "dimensions": ["minecraft:overworld"],
    "biomes": ["#minecraft:is_overworld"],
    "weather": "clear",
    "time": "day"
  },
  "settings": {
    "min_drop_distance": 48,
    "max_drop_distance": 128,
    "allow_liquid_landing": false
  }
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `schema_version` | Yes | Integer `1`. |
| `display_name` | Yes | Minecraft text component; use `text` or a resource-pack language key via `translate`. |
| `weight` | Yes | Integer 1–1,000,000; relative weight among eligible supply types. |
| `loot_table` | Yes | Loot table resource ID. |
| `appearance` | Yes | `mineral` for a blue label or `food` for an orange label; independent of loot. |
| `required_mods` | No | Mod IDs that must all be present. Defaults to `[]`. |
| `conditions` | No | Conditions for automatic scheduling. |
| `settings` | No | Per-type overrides. Omitted values inherit global settings. |

Unknown fields are rejected to catch spelling mistakes. JSON does not accept comments. Use numbers and booleans without quotation marks.

## Automatic conditions

| Field | Default | Accepted values |
| --- | --- | --- |
| `dimensions` | `[]` | Dimension IDs or dimension tags starting with `#`; any listed entry can match. Empty adds no restriction. |
| `biomes` | `[]` | Biome IDs or tags starting with `#`; any listed entry can match. |
| `weather` | `"any"` | `any`, `clear`, `rain`, `thunder`; rain includes thunderstorms. |
| `time` | `"any"` | `any`, `day`, `night`; day is tick 0–11999, night is 12000–23999. |

All supplied fields must match. Conditions use the target player's position and dimension; the landing point can be in a different biome. Weather uses the dimension's global state rather than local precipitation or snow. Types must also satisfy global `allowed_dimensions`.

At each interval the scheduler waits for the current aircraft to depart, selects a living non-spectator player in an allowed dimension, filters types, chooses one by weight, and searches its distance range for a landing point. An overdue check remains pending during a flight. Player/type/placement failure triggers a retry after one minute. Aircraft departure at tick 1000 permits another event, even if older cargo is still descending or crates remain. Landing, emptying or breaking a crate before that tick does not free the flight slot.

### Shared dimension groups (1.0.3+)

Dimension tags group **dimension IDs**, not dimension types. Sharing a dimension type does not make another dimension a member. Both Minecraft branches use `data/<namespace>/tags/dimension/<name>.json`.

For example, create `data/tacz_airdrop/tags/dimension/allowed_dimensions.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:overworld",
    {"id": "othermod:custom_dimension", "required": false}
  ]
}
```

The optional entry is included only when that dimension exists in the loaded world. Other datapacks can append to the same tag with `replace: false`, override earlier members with `replace: true`, or reference another dimension tag with a `#namespace:name` entry.

Each supply type can reference the shared group:

```json
"conditions": {
  "dimensions": ["#tacz_airdrop:allowed_dimensions"]
}
```

Automatic events must also pass the server whitelist. To let both filters follow the same group, set this in the server TOML:

```toml
allowed_dimensions = ["#tacz_airdrop:allowed_dimensions"]
```

The global default remains `["minecraft:overworld"]`. IDs and tags can be mixed in either list. An empty type list adds no restriction; an empty global whitelist allows no automatic events. Missing or empty referenced tags are reported by validation; invalid type definitions are excluded. Tag membership updates after `/reload`; new dimension definitions still require loading the world again. Administrator `spawn` and `crate` commands continue to bypass both filters.

## Aircraft route (1.0.6+)

The aircraft's visible route starts **600 blocks before** the landing point and ends **400 blocks after** it, at a height of 60 blocks above that point. Heading remains random. Flight speed stays at one block per tick (20 blocks per second at 20 TPS): flares release at 24, 26, and 28 seconds; cargo releases at 30 seconds; the aircraft departs at 50 seconds.

These offsets are relative to the landing point, not the selected player's location. With the default 64–150-block landing radius and a stationary player, the horizontal distance is about 450–750 blocks at appearance and 250–550 blocks at departure. Client visibility depends on entity tracking and streamed flight chunks. Engine sound follows the visible aircraft and fades to silence beyond 384 blocks; visibility and audibility remain limited by the client's tracking/view settings.

Since 1.0.11 the aircraft entity moves at its actual flight position instead of staying above the landing point. Model interpolation, server tracking and engine audio follow the same path, so approaching an aircraft does not move the player away from a hidden stationary anchor. A moving region ticket keeps only the current aircraft area active; it is moved between chunks and released on departure, cancellation or server shutdown. The landing area remains active while cargo is in flight. Old stationary-anchor entity saves are migrated using their captured flight age and heading.

Event and aircraft NBT from older builds translate their elapsed flight age during loading, preserving the aircraft's current position, flare progress, and cargo phase. Already-released cargo is not released again. In 1.0.9, landed crate expiration fields no longer delete blocks or supplies. The longer route is built in and is not a datapack or server-config setting.

## Server configuration and type settings

Configuration is at `<world>/serverconfig/airdrop_supply_drops-server.toml`. Put a copy in `defaultconfigs/airdrop_supply_drops-server.toml` to supply defaults for new worlds.

| Global setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Enable automatic scheduling. |
| `interval_min_seconds` | `1200` | Minimum interval between automatic delivery starts; positive integer. |
| `interval_max_seconds` | `1800` | Maximum interval; at least the minimum. Equal values give a fixed interval. |
| `allowed_dimensions` | `["minecraft:overworld"]` | Dimension IDs or `#dimension` tags eligible for automatic events. |
| `smoke_color` | `"#FF0000"` | Smoke color for all crates as `#RRGGBB`; selected by the server and sent in each particle packet. |
| `airborne_smoke_enabled` | `false` | Enable smoke while crates descend. Landed smoke stops when emptied or five minutes after landing. |

For a fixed ten-minute interval:

```toml
interval_min_seconds = 600
interval_max_seconds = 600
```

Changing either interval resets the pending check from the time the new config becomes effective. Unchanged interval settings preserve the pending check across restarts. A short interval still cannot overlap aircraft flights; a due check can proceed after departure. These values measure game ticks at 20 ticks per second, so a paused world pauses scheduling.

For example, green smoke with emission during descent:

```toml
smoke_color = "#00FF00"
airborne_smoke_enabled = true
```

These are global server settings, shared by mineral, food, and custom drops. They are read for each emission, so a server-config reload also updates existing drops. Particles already emitted finish fading with their original color. Clients use the color in the particle packet; local client settings do not select the color. `/reload` reloads datapacks, not these TOML settings; restart the server after editing its config if it does not reload automatically.

Use version 1.0.1 or later on both the server and clients: the colored smoke packet includes RGB data that older clients cannot decode.

The following global defaults can also be overridden in a type's `settings`:

| Setting | Default | Range and behavior |
| --- | --- | --- |
| `min_drop_distance` | `64` | Horizontal blocks; integer 0–200. |
| `max_drop_distance` | `150` | Integer 0–200; at least the resolved minimum. |
| `allow_liquid_landing` | `true` | Land above water, lava, or modded fluids without replacing them. |

A narrow distance range or low view distance can prevent finding a landing point. Candidate chunks must already be loaded. If fluid landing is disabled and changed terrain causes contact with fluid during descent, the event and cargo are deleted.

Distance is measured horizontally from the selected player's position at event creation. The landing point stays fixed if the player moves. At tick 600 the aircraft releases cargo 60 blocks above that point; release does not depend on its current distance from the player.

Crates have a fixed 27-slot inventory. Players can only take items out. Automated insertion/extraction is disabled. Crates and remaining contents persist indefinitely; mining drops four oak planks plus remaining supplies, with the same plank count for hand mining, normal tools, Silk Touch and Fortune. Pick-block returns no crate item. Smoke stops after 6000 game ticks from landing, or earlier when emptied. Its absolute deadline is saved and is not reset by chunk loading or a new singleplayer session. Color and airborne emission remain server settings; the five-minute landed duration is fixed.

Version 1.0.11 uses a roughly 13-block smoke column that gently spreads sideways with height: smoke rises at 0.10 blocks per tick for 120–129 ticks, with limited sideways drift and gradual sprite growth. Particle packets continue to use the server's RGB color and a 256-block recipient limit; actual visibility depends on loaded crate chunks and client particle/render settings. Aircraft lights use exposed fixtures, a steady white tail light, and an additive shader that does not darken downward-facing lamps. Their glow still respects opaque geometry and fog; it does not illuminate terrain.

Existing worlds keep their saved distance settings. To apply the new default there, set `min_drop_distance = 64` and `max_drop_distance = 150` in that world's server TOML. Type-specific distance overrides continue to take precedence. Install matching 1.0.11 client and server versions for the updated aircraft metadata.

The former server keys `max_active_events`, `landed_lifetime_seconds` and `reset_on_rejoin` no longer control behavior and can be removed from old TOML files. Valid legacy type fields `landed_lifetime_seconds` and `reset_on_rejoin` are still accepted, but ignored, so existing packs continue loading. Old NBT timer fields are ignored for block/inventory lifetime; when possible, the former expiration and captured lifetime recover the original landing time for the new five-minute smoke limit. Completed landing/flight records retire without loading or removing their crate chunks; completed crates no longer appear in `status` or support cancellation by event UUID.

Aircraft models, flight paths, per-player scheduling, and Java/KubeJS lifecycle events are not configurable through this schema.

## Loot and exact probabilities

Create a vanilla chest table at `data/<namespace>/loot_table/<path>.json`. Reference it as `<namespace>:<path>`. Override `data/airdrop_supply_drops/loot_table/airdrop/mineral.json` to replace built-in mineral loot.

`pools` run independently, `rolls` controls draw count, entry `weight` values are relative, and `minecraft:set_count` controls quantities. Item entries use their registered item ID in `name`, including other installed mods' items. In this version, nested `minecraft:loot_table` entries use `value` for the referenced table ID; item/tag entries still use `name`.

For a **1% chance per crate** to generate one diamond, add a separate pool that runs once:

```json
{
  "rolls": 1,
  "conditions": [{"condition": "minecraft:random_chance", "chance": 0.01}],
  "entries": [{
    "type": "minecraft:item",
    "name": "minecraft:diamond",
    "functions": [{"function": "minecraft:set_count", "count": 1}]
  }]
}
```

The complete example is [survival.json](example_datapack/data/example_airdrops/loot_table/airdrop/survival.json). Do not add diamonds to other pools if you want a maximum of one. Keep total output within the crate's 27 slots; generated loot can overflow capacity.

The built-in mineral table instead uses **1% legendary weight per roll**, with 8–14 rolls per crate. Each legendary result gives one diamond; multiple successes can produce multiple diamonds. Weights do not have to sum to 100.

The medical example includes honey bottles, healing potions, and golden apples. Potions use `minecraft:set_components` to set `minecraft:potion_contents`. This mod adds no resource items.

## Optional mods and validation

Use registered mod IDs in `required_mods`, not display names. A type is skipped if any required mod is absent. This only gates the type; it does not stop Minecraft from loading other loot tables in the datapack. Ship optional-mod tables in a separate optional datapack, or reference tables supplied by that mod, to avoid missing-item errors.

Definitions are checked at startup and after `/reload`. Invalid types are excluded while valid ones remain available. `/airdrop_supply_drops validate` checks loaded data; it does not reread files. Run `/reload` after editing files.

Checks cover field types/ranges, resolved distances, dimensions, the server dimension whitelist, biomes, nonempty dimension/biome/item tags, tables, standard item entries, missing subtables, and cycles. Diagnostics identify the type, server config, and, when possible, the loot field. If Minecraft rejects a table or tag before this mod can inspect it, check `logs/latest.log` for the parse error. Custom entries/functions/conditions are validated by Minecraft or their owning mod; this command is not a complete third-party validator.

## TACZ interact key (1.0.5+)

TACZ uses its own whitelist to decide whether to show the interaction prompt and let the interact key trigger normal item/block use. Its official 1.20.1 implementation checks the block tag `tacz:interact_key/whitelist`; having an inventory or right-click handler alone does not qualify a block. See its [whitelist predicate](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/config/util/InteractKeyConfigRead.java) and [key handler](https://github.com/MCModderAnchor/TACZ/blob/b482eff8c94a733ac8d0910193fca3893954027c/src/main/java/com/tacz/guns/client/input/InteractKey.java).

The mod JAR appends `airdrop_supply_drops:airdrop_crate` to that tag using `replace: false`, preserving other packs' whitelist entries. All landed supply types share this block ID, so mineral, food, and custom crates are covered. No TACZ classes or required dependency are added; the tag can load when TACZ is absent. Aircraft and descending cargo are not interactive containers.

This Minecraft 1.21.1 branch places the tag at `data/tacz/tags/block/interact_key/whitelist.json`. The 1.20.1 branch uses `tags/blocks`. The referenced official TACZ implementation is for Forge 1.20.1; this branch supplies the corresponding 1.21.1 tag for ports that retain the same contract. A specific 1.21.1 port has not been verified.

Install the matching 1.0.5 JAR on both client and server, then create mineral and food crates with the administrator `crate` commands. Holding a TACZ gun and aiming at each landed crate should show TACZ's configured interact key (default O); pressing it should open the usual 27-slot, take-only menu. Check a chest or villager too, and verify empty-hand right-click still opens the crate. The upstream predicate and packaging have been checked; the real prompt and key action still require this client test.

TACZ's block blacklist takes precedence over the whitelist. A later datapack using `replace: true` can also remove this entry. If other interaction prompts work but crates still do not, inspect TACZ's `InteractKeyBlacklistBlocks`, `tacz:interact_key/blacklist`, and overrides of `tacz:interact_key/whitelist`.

## Resource packs and persistence

Client resources use `assets/airdrop_supply_drops/`. Follow `src/main/resources/assets/airdrop_supply_drops/` to replace textures, sounds, block models, and translations. Parachute canopy models and cord attachment points can also be replaced; see [PARACHUTE-PBR.md](PARACHUTE-PBR.md) and the separate `example_resourcepack`. Aircraft geometry remains Java-defined. LabPBR 1.3 maps are bundled and become available automatically with a compatible shader pack; vanilla uses the original colour textures. The server's `validate` command does not check resource packs.

Cargo, display name, appearance, and resolved settings are saved when an event starts. Reloading affects future deliveries; existing ones keep their loot. Removing a type does not reroll an existing crate. Landed supplies persist until collected or the block is removed. Smoke deadlines survive unloading and restart; player mining drops remaining supplies and four oak planks.

## Check a custom pack

1. Install the example, reload, and validate. Mineral, food, medical, and survival types should be listed without errors.
2. Generate a medical crate; check its orange label and supplies. Leave it for more than five minutes: smoke should stop while the crate and supplies remain.
3. Empty a crate; check that it remains without smoke. Mine a crate normally and with Silk Touch/Fortune: each should yield four oak planks plus remaining supplies, with no crate item.
4. Reopen singleplayer and unload/reload a crate chunk; verify inventory persistence and that smoke does not get a new five-minute window.
5. Shorten automatic intervals in a test world and change weather/time to check conditions. Administrator commands bypass these conditions.
6. Introduce a misspelled item ID or setting in a test copy, verify diagnostics, then fix it and reload.

## Signal tube integration

See [SIGNAL-TUBE.md](SIGNAL-TUBE.md) for server probabilities, projectile settings, type binding, commands and chest loot customization. Pack-defined loaded types participate in random and bound tube selection. There is no built-in crafting recipe.
