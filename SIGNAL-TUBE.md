# Signal tubes

Signal tubes are single-use rectangular olive tubes with a square open mouth, metal sleeve and grip ribs. Right-click fires a visible flare along the player's aim and immediately requests one supply delivery. No built-in recipe is provided; modpacks can add recipes for `airdrop_supply_drops:signal_tube`.

## Default behaviour

- Found as a bonus in **vanilla structure chest loot tables** (`minecraft:chests/*`), including dungeons, ruins and abandoned mineshafts. Each loot-table roll has a 5% chance to add one tube; existing supplies are retained. Existing opened chests are not refilled. The default bonus does not apply to this mod's airdrop crates, mob loot, fishing or other mods' chest tables.
- 30% of bonus tubes have no suffix and request a random valid loaded airdrop type, using its configured weight. The remaining 70% bind one valid type by weight and display its name as a suffix. Datapack-defined types participate automatically.
- A bound tube stores the **type ID**, not an editable name. Anvil renaming cannot change the target. If its type is removed, unavailable or has invalid loot, firing fails without consuming the item.
- Flare speed is 1.5 blocks per tick, with no gravity. It bursts after 48 blocks of actual travel; an entity or block collision bursts sooner. It deals 3 raw damage points (1.5 hearts before armour and other modifiers) to visible living targets within 3 blocks. Only a directly struck living target is ignited, for 5 seconds. Solid walls shield targets, and the burst does not break blocks or create ground fire. PvP and friendly-fire restrictions are respected.
- One successful shot consumes one tube in survival; creative mode retains it. A short one-second cooldown prevents accidental repeat shots.
- The existing rule that allows only one aircraft in flight applies to signal tubes. If a plane has not departed, the shot is rejected without firing or spending a tube. The request is not queued. Landed crates do not block new deliveries.
- Landing uses the existing safe surface selection around the shooter, **64–150 blocks by default**. Aiming controls the flare direction, not the landing point. A player underground still requests a surface delivery. No safe loaded landing point means no shot and no item consumption.
- Signal requests respect the server `allowed_dimensions` list. Like the operator spawn command, they intentionally summon a type without its automatic weather/time/biome conditions or the automatic interval. `enabled` controls automatic scheduling only. The projectile never requests another delivery when it bursts or reloads.

## Server settings

Edit the world's `serverconfig/airdrop_supply_drops-server.toml`:

```toml
[signal_tube]
loot_chance = 0.05
random_variant_chance = 0.30
range = 48.0
speed = 1.5
burst_radius = 3.0
damage = 3.0
direct_hit_burn_seconds = 5
```

Set `loot_chance = 0.0` to disable the default chest bonus. The ranges accepted by the server are: chances 0–1; travel 1–256 blocks; speed 0.25–4 blocks/tick; burst radius 0.1–8; damage 0–100; ignition 0–60 seconds. Projectile combat settings are captured at launch and saved with the projectile. An in-flight flare retains its remaining travel distance across chunk unloads or a server restart.

## Operator commands

These commands require permission level 2 and a player command source:

```mcfunction
# Unsuffixed random tube
/airdrop_supply_drops signal
# Specific type, including datapack-defined IDs
/airdrop_supply_drops signal airdrop_supply_drops:mineral
/airdrop_supply_drops signal airdrop_supply_drops:food
/airdrop_supply_drops signal example_airdrops:survival
```

For manually authored item data, `airdrop_type` is the type ID and optional `airdrop_label` is a serialized Minecraft text component. Minecraft 1.20.1 stores these in the stack's NBT; 1.21.1 stores them inside `minecraft:custom_data`. The dedicated command creates correct version-specific data and labels.

## Pack integration and artwork

The chest bonus uses Forge's [Global Loot Modifiers](https://docs.minecraftforge.net/en/1.21.x/resources/server/glm/) with `replace: false`, rather than replacing vanilla loot tables. Packs can override `data/airdrop_supply_drops/loot_modifiers/signal_tube_structure_chests.json` to add conditions, including restricting specific structure tables. The built-in modifier still limits itself to vanilla `chests/` table IDs. Set the server chance to zero when supplying a different loot distribution.

The editable model is `art/source/signal_tube.bbmodel`; the baked model is `assets/airdrop_supply_drops/models/item/signal_tube.json`. Four diffuse materials and their LabPBR `_n`/`_s` companions live under `textures/item/`. Compatible shaders read the material maps automatically; ordinary rendering uses the diffuse textures. See [texture source and generation prompt](art/source/signal-tube-texture-prompts.md).
