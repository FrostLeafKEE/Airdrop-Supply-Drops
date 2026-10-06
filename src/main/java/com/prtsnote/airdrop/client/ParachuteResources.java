package com.prtsnote.airdrop.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;

/** Client-only appearance data. A resource pack never changes drop physics or loot. */
public final class ParachuteResources extends SimplePreparableReloadListener<ParachuteResources.Rig> {
    public static final ModelResourceLocation CANOPY = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath("airdrop_supply_drops", "entity/parachute_canopy"), "standalone");
    public static final ResourceLocation RIG = ResourceLocation.fromNamespaceAndPath(
            "airdrop_supply_drops", "parachute/rigging.json");
    private static final Rig DEFAULT = new Rig(3.8F, 0.35F, 3F, 1.15F, 0.23F, 0.036F,
            TexturedBox.texture("canopy_ivory"), List.of(
            new Cord(new Point(-0.42F, 1, -0.42F), new Point(0, 0, 0)),
            new Cord(new Point(-0.42F, 1, 0.42F), new Point(0, 0, 16)),
            new Cord(new Point(0.42F, 1, -0.42F), new Point(16, 0, 0)),
            new Cord(new Point(0.42F, 1, 0.42F), new Point(16, 0, 16))));
    public static final ParachuteResources INSTANCE = new ParachuteResources();
    private volatile Rig current = DEFAULT;

    private ParachuteResources() {}

    public Rig rig() { return current; }
    public static Rig defaultRig() { return DEFAULT; }

    /** Forge can bake a separate missing-model instance, so reference equality is insufficient. */
    public static boolean isUsableCanopy(net.minecraft.client.resources.model.BakedModel model) {
        var random = net.minecraft.util.RandomSource.create(0);
        var missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        for (int face = -1; face < net.minecraft.core.Direction.values().length; face++) {
            var direction = face < 0 ? null : net.minecraft.core.Direction.values()[face];
            for (var quad : model.getQuads(null, direction, random)) {
                if (!quad.getSprite().contents().name().equals(missing)) return true;
            }
        }
        return false;
    }

    @Override
    protected Rig prepare(ResourceManager resources, ProfilerFiller profiler) {
        return loadRig(resources, RIG);
    }

    public static Rig loadRig(ResourceManager resources, ResourceLocation path) {
        try (var reader = resources.getResourceOrThrow(path).openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception error) {
            LogUtils.getLogger().warn("Invalid parachute rig {}; using the default rig", path, error);
            return DEFAULT;
        }
    }

    @Override
    protected void apply(Rig rig, ResourceManager resources, ProfilerFiller profiler) { current = rig; }

    public static Rig parse(JsonObject json) {
        if (!json.has("format") || number(json, "format", 1, 1) != 1) {
            throw new IllegalArgumentException("Parachute rig format must be 1");
        }
        float width = number(json, "open_width", 0.1F, 16);
        float closedWidth = number(json, "closed_width", 0.05F, width);
        float height = number(json, "open_height", 1, 16);
        float closedHeight = number(json, "closed_height", 1, height);
        float vertical = number(json, "closed_vertical_scale", 0.01F, 1);
        float thickness = number(json, "cord_width", 0.005F, 0.25F);
        ResourceLocation texture = ResourceLocation.parse(json.get("cord_texture").getAsString());
        if (!texture.getPath().startsWith("textures/") || !texture.getPath().endsWith(".png")) {
            throw new IllegalArgumentException("cord_texture must reference a textures/...png resource");
        }
        JsonArray cords = json.getAsJsonArray("cords");
        if (cords == null || cords.size() < 1 || cords.size() > 64) {
            throw new IllegalArgumentException("Expected 1 to 64 suspension cords");
        }
        var parsed = new java.util.ArrayList<Cord>();
        for (var value : cords) {
            JsonObject cord = value.getAsJsonObject();
            parsed.add(new Cord(point(cord.getAsJsonArray("crate"), -1, 2),
                    point(cord.getAsJsonArray("canopy"), -16, 32)));
        }
        return new Rig(width, closedWidth, height, closedHeight, vertical, thickness, texture, List.copyOf(parsed));
    }

    private static float number(JsonObject json, String key, float min, float max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("Missing numeric field: " + key);
        }
        float value = json.get(key).getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Out of range: " + key);
        }
        return value;
    }

    private static Point point(JsonArray json, float min, float max) {
        if (json == null || json.size() != 3) throw new IllegalArgumentException("Expected a 3D attachment point");
        float[] values = new float[3];
        for (int index = 0; index < 3; index++) {
            if (!json.get(index).isJsonPrimitive() || !json.get(index).getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Attachment coordinates must be numbers");
            }
            values[index] = json.get(index).getAsFloat();
            if (!Float.isFinite(values[index]) || values[index] < min || values[index] > max) {
                throw new IllegalArgumentException("Attachment coordinate out of range");
            }
        }
        return new Point(values[0], values[1], values[2]);
    }

    public record Point(float x, float y, float z) {}
    public record Cord(Point crate, Point canopy) {}
    public record Rig(float openWidth, float closedWidth, float openHeight, float closedHeight,
                      float closedVerticalScale, float cordWidth, ResourceLocation cordTexture, List<Cord> cords) {}
}
