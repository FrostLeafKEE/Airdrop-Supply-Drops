# Example datapack

For **Minecraft 1.20.1**, using datapack format **15**. Adds medical and survival supply types without replacing the built-in mineral and food drops.

Copy this folder into `<world>/datapacks/`, with `pack.mcmeta` at the top level. Then run:

```text
/reload
/airdrop_supply_drops validate
/airdrop_supply_drops crate example_airdrops:medical
/airdrop_supply_drops spawn example_airdrops:survival
```

Medical supplies demonstrate healing potions, optional automatic conditions, a custom landing distance and dry-ground landing. Survival supplies demonstrate a separate one-roll pool with a 1% chance per crate to award one diamond. Landed crates persist indefinitely; smoke stops after five minutes or when emptied. Old type fields `landed_lifetime_seconds` and `reset_on_rejoin` are accepted for compatibility and ignored.

Survival supplies use `#example_airdrops:allowed_dimensions`, a shared dimension-ID group containing only `minecraft:overworld` by default. Append dimension IDs in `data/example_airdrops/tags/dimension/allowed_dimensions.json`. To let automatic events follow that group outside the Overworld, also set `allowed_dimensions = ["#example_airdrops:allowed_dimensions"]` in the server config. Requires mod version 1.0.3 or later.

See the [integration guide](../INTEGRATION.md) for fields, loot-table syntax, and commands. Administrator commands bypass automatic conditions. This example is installed separately and is not bundled in the mod JAR.
