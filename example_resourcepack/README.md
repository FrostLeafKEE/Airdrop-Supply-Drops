# Parachute appearance example

This client resource pack replaces the default canopy with nine panels and eight suspension cords. It uses the mod's existing cloth textures and bundled LabPBR maps. It contains no collaboration assets, loot, server settings or shader pack.

Put this folder (or its ZIP, with `pack.mcmeta` at the root) in your Minecraft `resourcepacks` directory. Enable it above the mod's resources and reload with F3+T. Disable it to restore the original canopy. Pack formats 15 (1.20.1) and 34 (1.21.1) are declared; both versions use the same appearance files.

Edit `assets/airdrop_supply_drops/models/entity/parachute_canopy.json` using Blockbench's Java Block/Item format. Edit `assets/airdrop_supply_drops/parachute/rigging.json` to move cord attachment points. See [PARACHUTE-PBR.md](../PARACHUTE-PBR.md) in the source release for coordinates, limits and PBR requirements. The model's `elements[].name` fields are editor metadata.
