# Aircraft material sources, version 1.0.8

Generated with the built-in imagegen tool. `aircraft-body-v2.png` uses the original body tile as a palette reference; `aircraft-glass-v2.png` is a new material. The slicing script reduces both sources to 128 by 128 pixels with nearest-neighbor sampling. The original supply atlas is retained.

## Body prompt

Use case: stylized-concept.
Asset type: one seamless square albedo material tile for the Minecraft Forge Air Supply transport aircraft.
The attached small image is a palette and material reference only. Create a refined new version of this olive-green painted aircraft skin. Flat orthographic material texture, full bleed, no rendered object or perspective.
Style: restrained crisp Minecraft-compatible pixel art, constructed on a logical 128 by 128 pixel grid, no smooth photorealistic noise. Matte olive-drab painted metal with a few large rectangular skin panels, fine dark seams, subtle one-pixel edge highlights, sparse small rivets along seams, and tiny restrained paint chips at a few seams. Predominantly clean olive paint; all wear is very subtle. Approximately two panels across and two high, offset panel rows. Straight neat seams and rivets of consistent scale. No thick embossed framing.
Color palette must stay close to the reference: muted olive greens, dark olive seams, a few soft sage highlights; no bright colors, no rust or grunge wash.
Every edge must wrap seamlessly onto its opposite edge; allow details to continue across tile boundaries. Uniform diffuse illumination without baked shadows or directional lighting.
No text, insignia, bolts as giant circles, logos, borders, watermarks, transparency, aircraft silhouette, glass, rubber, or atlas layout. Output only this one tile.

## Glass prompt

Use case: stylized-concept.
Asset type: a single square seamless opaque albedo material tile for Minecraft aircraft cockpit glass.
Create clean dark blue-gray aircraft glass with a very restrained broad diagonal reflection of a pale overcast sky. No window outline or frame: this texture will cover differently sized glass polygons and the model already has frames. Full-bleed flat orthographic material only. Minecraft-compatible restrained pixel art on a logical 128 by 128 grid, crisp pixels, no noise, no photoreal blur. Most pixels are dark muted slate blue (#263b48 to #405969). Only a few sparse, soft-looking but pixelated diagonal reflection bands in muted medium blue-gray (#6b8791), never white. The diagonal bands must wrap seamlessly at all image boundaries. Bright reflection occupies less than 12% of the tile. Uniform diffuse light; no baked 3D shadow, no bevels, no scratches or chips, no panes or grids or borders, no objects, text, logos, symbols, watermarks, transparency, or atlas. Only one seamless material swatch.
