# Artwork sources

The mod uses a block-style aircraft, wooden crates with blue mineral or orange food labels, and an ivory/red parachute. Models are defined in Java or block-model JSON under `src/main/`; textures are under `src/main/resources/assets/airdrop_supply_drops/textures/`.

The 1.0.4 aircraft mesh is defined in `AircraftAppearance.java`: octagonal fuselage sections, sloped cockpit panels, swept wings and stabilizers, engine nacelles, exhausts, four-bladed propellers, cargo-ramp ribs, gear fairings and antennas. Geometry and outward normals are computed once and reused by the renderer with the existing four aircraft materials.

Seven light fixtures use Minecraft's white texture and soft particle sprite through an emissive render pass. Navigation lights stay on; wing-tip strobes produce two 100 ms flashes every 1.5 seconds at 20 TPS, with the tail strobe offset by 100 ms. Upper and lower red beacons pulse in alternation. Timing follows the synchronized, smoothed flight age and resumes with the saved entity age. The fixtures are visual effects; they emit no particles and require no extra position or flash packets.

The texture atlas, `source/supply-atlas.png`, and logo, `logo/airdrop-logo-v1.png`, were created with AI image-generation tools for this project. The atlas is retained as an editable source. Its three unused material-icon cells are not exported; the mod adds no resource items.

On Windows, run the slicing script from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File art/build-textures.ps1
```

The script uses System.Drawing and nearest-neighbor resizing to write 64×64 block/entity textures and the 32×32 smoke sprite into the resource tree. The release JAR contains the textures; the source archive also includes the atlas and script.

Artwork uses the project's LGPL-3.0-only license. See [LICENSE](../LICENSE).

Configurable smoke uses Minecraft's white `minecraft:generic_7` particle sprite, tinted with RGB data sent by the server. The original red sprite remains as an artwork source but is no longer selected by default. Resource packs can override `assets/airdrop_supply_drops/particles/red_smoke.json`; use a neutral grayscale sprite to preserve configurable colors.
