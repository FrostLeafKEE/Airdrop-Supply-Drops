# Migrating from the old mod ID

The internal mod ID changed from `airdrop` to **`airdrop_supply_drops`** to avoid a naming conflict. The display name is **Airdrop: Supply Drops**. The Java package `com.prtsnote.airdrop` remains unchanged and is not a registry namespace.

## Before upgrading

Back up your world. Using the old build, collect or cancel existing deliveries before replacing the JAR. Old crates, aircraft, and descending entities are **not automatically converted** to new IDs. Do not directly upgrade if you need to preserve those objects. No compatibility aliases are registered under the old ID.

The new build uses `/airdrop_supply_drops`, default types `airdrop_supply_drops:mineral` and `airdrop_supply_drops:food`, and `<world>/serverconfig/airdrop_supply_drops-server.toml`. Copy customized values from `airdrop-server.toml`; the old configuration and event records are not loaded automatically.

## Update packs and commands

Replace references to this mod's `airdrop:` namespace with `airdrop_supply_drops:`. Rename matching `data/airdrop/` and `assets/airdrop/` folders, update this mod ID in `required_mods`, and update language keys such as `block.airdrop_supply_drops.airdrop_crate`. Update command blocks and scripts using `/airdrop`.

Keep your own namespaces, such as `example_airdrops`, unchanged. Keep the `airdrop/` path inside loot table IDs unchanged, for example `example_airdrops:airdrop/medical`. Use the supplied example as a reference.

These notes cover the mod ID change. Minecraft 1.20.1 and 1.21.1 use different datapack formats and loot syntax; use the example from the matching branch when migrating game versions.
