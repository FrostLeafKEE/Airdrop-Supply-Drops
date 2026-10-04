# Artwork sources

The mod uses a block-style aircraft, wooden crates with blue mineral or orange food labels, and an ivory/red parachute. Models are defined in Java or block-model JSON under `src/main/`; textures are under `src/main/resources/assets/airdrop_supply_drops/textures/`.

The 1.0.4 aircraft mesh is defined in `AircraftAppearance.java`: octagonal fuselage sections, sloped cockpit panels, swept wings and stabilizers, engine nacelles, exhausts, four-bladed propellers, cargo-ramp ribs, gear fairings and antennas. Geometry and outward normals are computed once and reused by the renderer with the existing four aircraft materials.

In 1.0.7 the cockpit uses six framed panes: two central windshield panes, two forward corner panes and two pilot side windows. Their vertices follow the same fuselage section rings and surface triangles as the hull, with a small outward offset to avoid z-fighting. Glazing is triangulated into planar faces instead of using warped side quads. This removes the floating side-window edge and makes the windshield continue around the nose. Aircraft textures, cargo windows, flight timing and lights are unchanged.

In 1.0.8 the body and glass materials are 128×128 tiles. The olive skin has cleaner panel seams, small rivets and restrained paint wear; blue-gray glass has a subtle reflection without a painted window frame. The independent AI-generated sources are `source/aircraft-body-v2.png` and `source/aircraft-glass-v2.png`; the exact prompts and tool provenance are retained in [source/aircraft-texture-prompts.md](source/aircraft-texture-prompts.md). Frame and rubber retain their original 64×64 images. Aircraft surface UVs are cached with the mesh and project model-space coordinates onto each face's dominant plane, with one tile per four model blocks. Long panels repeat instead of stretching a full image, and degenerate triangle vertices share UVs. Light sprites retain their original full-image UVs. The mesh and gameplay are unchanged.

Seven light fixtures use Minecraft's white texture and soft particle sprite through an emissive render pass. Navigation lights stay on; wing-tip strobes produce two 100 ms flashes every 1.5 seconds at 20 TPS, with the tail strobe offset by 100 ms. Upper and lower red beacons pulse in alternation. Timing follows the synchronized, smoothed flight age and resumes with the saved entity age. The fixtures are visual effects; they emit no particles and require no extra position or flash packets.

The texture atlas, `source/supply-atlas.png`, and logo, `logo/airdrop-logo-v1.png`, were created with AI image-generation tools for this project. The atlas is retained as an editable source. Its three unused material-icon cells are not exported; the mod adds no resource items.

On Windows, run the slicing script from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File art/build-textures.ps1
```

The script uses System.Drawing and nearest-neighbor resizing to write the 128×128 aircraft body/glass overrides, the remaining 64×64 block/entity textures and the 32×32 smoke sprite into the resource tree. The release JAR contains the textures; the source archive also includes the original atlas, independent aircraft sources, prompts and script. Aircraft resource-pack replacements should tile and keep texture metadata `clamp: false` (the default), because UVs repeat beyond the 0–1 range.

Artwork uses the project's LGPL-3.0-only license. See [LICENSE](../LICENSE).

Configurable smoke uses Minecraft's white `minecraft:generic_7` particle sprite, tinted with RGB data sent by the server. The original red sprite remains as an artwork source but is no longer selected by default. Resource packs can override `assets/airdrop_supply_drops/particles/red_smoke.json`; use a neutral grayscale sprite to preserve configurable colors.
