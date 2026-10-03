# Example datapack

For **Minecraft 1.21.1**, using datapack format **48**. Adds medical and survival supply types without replacing the built-in mineral and food drops.

Copy this folder into `<world>/datapacks/`, with `pack.mcmeta` at the top level. Then run:

```text
/reload
/airdrop_supply_drops validate
/airdrop_supply_drops crate example_airdrops:medical
/airdrop_supply_drops spawn example_airdrops:survival
```

Medical supplies demonstrate healing potions, optional automatic conditions, a ten-minute lifetime, no timer reset on singleplayer rejoin, and dry-ground landing. Survival supplies demonstrate a separate one-roll pool with a 1% chance per crate to award one diamond.

See the [integration guide](../INTEGRATION.md) for fields, loot-table syntax, and commands. Administrator commands bypass automatic conditions. This example is installed separately and is not bundled in the mod JAR.
