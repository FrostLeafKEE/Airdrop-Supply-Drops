package com.prtsnote.airdrop.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.data.AirdropAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/** One immutable client snapshot per resource reload; no server registry or client handshake is needed. */
public final class AirdropAppearanceResources extends SimplePreparableReloadListener<AirdropAppearanceResources.Snapshot> {
    public static final AirdropAppearanceResources INSTANCE = new AirdropAppearanceResources();
    public static final ResourceLocation MINERAL_MODEL = modelId(new ResourceLocation("airdrop_supply_drops:block/airdrop_crate"));
    public static final ResourceLocation FOOD_MODEL = modelId(new ResourceLocation("airdrop_supply_drops:block/airdrop_crate_food"));
    private volatile Snapshot current = new Snapshot(Map.of(), Map.of());
    private final Map<BakedModel, Boolean> usable = new java.util.concurrent.ConcurrentHashMap<>();

    private AirdropAppearanceResources() {}
    public record Definition(ResourceLocation crate, ResourceLocation canopy, ResourceLocation rig, ResourceLocation fallback) {}
    public record Snapshot(Map<ResourceLocation, Definition> definitions, Map<ResourceLocation, ParachuteResources.Rig> rigs) {}
    public static ResourceLocation modelId(ResourceLocation id) { return id; }

    public static Definition parse(JsonObject json) {
        for (String key : json.keySet()) if (!java.util.Set.of("format", "crate_model", "parachute_model", "parachute_rig", "fallback").contains(key)) {
            throw new IllegalArgumentException("Unknown appearance field: " + key);
        }
        var format = json.get("format");
        if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()
                || format.getAsBigDecimal().intValueExact() != 1) throw new IllegalArgumentException("Appearance format must be 1");
        var crate = modelId(id(json, "crate_model"));
        var canopy = json.has("parachute_model") ? modelId(id(json, "parachute_model")) : ParachuteResources.CANOPY;
        var rig = json.has("parachute_rig") ? id(json, "parachute_rig") : ParachuteResources.RIG;
        if (!rig.getPath().endsWith(".json")) throw new IllegalArgumentException("parachute_rig must reference a JSON resource");
        var fallback = json.has("fallback") ? AirdropAppearance.parse(json.get("fallback").getAsString()) : AirdropAppearance.MINERAL;
        if (!fallback.equals(AirdropAppearance.MINERAL) && !fallback.equals(AirdropAppearance.FOOD)) {
            throw new IllegalArgumentException("fallback must be mineral or food");
        }
        return new Definition(crate, canopy, rig, fallback);
    }

    private static ResourceLocation id(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a namespace:path resource ID");
        }
        if (!value.getAsString().contains(":")) throw new IllegalArgumentException(key + " must be a namespace:path resource ID");
        return AirdropAppearance.parse(value.getAsString());
    }

    public static Map<ResourceLocation, Definition> discover(ResourceManager resources) {
        var result = new LinkedHashMap<ResourceLocation, Definition>();
        resources.listResources("airdrop_appearances", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            try (var reader = resource.openAsReader()) {
                String name = path.getPath().substring("airdrop_appearances/".length(), path.getPath().length() - 5);
                result.put(new ResourceLocation(path.getNamespace(), name), parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception error) {
                LogUtils.getLogger().warn("Invalid airdrop appearance {}; using built-in fallback: {}", path, error.getMessage());
            }
        });
        return Map.copyOf(result);
    }

    public static void registerModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(MINERAL_MODEL); event.register(FOOD_MODEL); event.register(ParachuteResources.CANOPY);
        for (var definition : discover(Minecraft.getInstance().getResourceManager()).values()) {
            event.register(definition.crate()); event.register(definition.canopy());
        }
    }

    @Override protected Snapshot prepare(ResourceManager resources, ProfilerFiller profiler) {
        var definitions = discover(resources);
        var rigs = new LinkedHashMap<ResourceLocation, ParachuteResources.Rig>();
        for (var definition : definitions.values()) if (!definition.rig().equals(ParachuteResources.RIG)) {
            rigs.computeIfAbsent(definition.rig(), path -> ParachuteResources.loadRig(resources, path));
        }
        return new Snapshot(definitions, Map.copyOf(rigs));
    }
    @Override protected void apply(Snapshot snapshot, ResourceManager resources, ProfilerFiller profiler) {
        usable.clear(); current = snapshot;
    }

    public Definition definition(ResourceLocation appearance) { return current.definitions().get(appearance); }
    public BakedModel crate(ResourceLocation appearance) {
        var definition = definition(appearance);
        var fallback = definition != null ? definition.fallback() : appearance;
        var manager = Minecraft.getInstance().getModelManager();
        var standard = manager.getModel(fallback.equals(AirdropAppearance.FOOD) ? FOOD_MODEL : MINERAL_MODEL);
        if (definition == null) return standard;
        var custom = manager.getModel(definition.crate());
        return isUsable(custom) ? custom : standard;
    }
    public boolean isUsable(BakedModel model) { return usable.computeIfAbsent(model, ParachuteResources::isUsableCanopy); }
    public ResourceLocation canopy(ResourceLocation appearance) {
        var definition = definition(appearance);
        if (definition == null) return ParachuteResources.CANOPY;
        var model = Minecraft.getInstance().getModelManager().getModel(definition.canopy());
        return isUsable(model) ? definition.canopy() : ParachuteResources.CANOPY;
    }
    public ParachuteResources.Rig rig(ResourceLocation appearance) {
        var snapshot = current;
        var definition = snapshot.definitions().get(appearance);
        // A missing canopy uses the matching default rig rather than dangling custom cords.
        if (definition == null || !canopy(appearance).equals(definition.canopy()) || definition.rig().equals(ParachuteResources.RIG)) {
            return ParachuteResources.INSTANCE.rig();
        }
        return snapshot.rigs().getOrDefault(definition.rig(), ParachuteResources.defaultRig());
    }
}
