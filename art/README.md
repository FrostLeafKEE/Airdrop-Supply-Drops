# Artwork sources

The mod uses a block-style aircraft, wooden crates with blue mineral or orange food labels, and an ivory/red parachute. Models are defined in Java or block-model JSON under `src/main/`; textures are under `src/main/resources/assets/airdrop_supply_drops/textures/`.

The texture atlas, `source/supply-atlas.png`, and logo, `logo/airdrop-logo-v1.png`, were created with AI image-generation tools for this project. The atlas is retained as an editable source. Its three unused material-icon cells are not exported; the mod adds no resource items.

On Windows, run the slicing script from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File art/build-textures.ps1
```

The script uses System.Drawing and nearest-neighbor resizing to write 64×64 block/entity textures and the 32×32 smoke sprite into the resource tree. The release JAR contains the textures; the source archive also includes the atlas and script.

Artwork uses the project's LGPL-3.0-only license. See [LICENSE](../LICENSE).

Configurable smoke uses Minecraft's white `minecraft:generic_7` particle sprite, tinted with RGB data sent by the server. The original red sprite remains as an artwork source but is no longer selected by default. Resource packs can override `assets/airdrop_supply_drops/particles/red_smoke.json`; use a neutral grayscale sprite to preserve configurable colors.
