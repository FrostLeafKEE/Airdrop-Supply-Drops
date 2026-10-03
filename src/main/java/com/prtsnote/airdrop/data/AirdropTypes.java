package com.prtsnote.airdrop.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AirdropTypes extends SimpleJsonResourceReloadListener {
    public record Type(ResourceLocation id, Component name, int weight, ResourceLocation lootTable, String appearance,
                       AirdropRules.Conditions conditions, AirdropRules.Overrides settings) {}
    private static Map<ResourceLocation, Type> types = Map.of();
    private static Map<ResourceLocation, Type> candidates = Map.of();
    private static java.util.List<String> parseErrors = java.util.List.of();
    private static java.util.List<String> diagnostics = java.util.List.of();
    public static java.util.List<String> diagnostics() { return diagnostics; }

    public AirdropTypes() {
        super(new Gson(), "airdrop_types");
    }

    public static Map<ResourceLocation, Type> all() { return types; }
    public static void clear() { types = Map.of(); candidates = Map.of(); parseErrors = java.util.List.of(); diagnostics = java.util.List.of(); }

    static int integer(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    public static Type parse(ResourceLocation id, JsonObject json) {
        AirdropRules.fields(json, java.util.Set.of("schema_version", "display_name", "weight", "loot_table", "appearance",
                "conditions", "settings", "required_mods"), "type");
        if (integer(json, "schema_version") != 1) {
            throw new IllegalArgumentException("Unsupported schema_version");
        }
        int weight = integer(json, "weight");
        if (weight < 1 || weight > 1000000) throw new IllegalArgumentException("weight must be 1..1000000");
        String appearance = GsonHelper.getAsString(json, "appearance");
        if (!appearance.equals("mineral") && !appearance.equals("food")) {
            throw new IllegalArgumentException("appearance must be mineral or food");
        }
        Component name = Component.Serializer.fromJson(json.get("display_name"));
        if (name == null) throw new IllegalArgumentException("Missing display_name");
        return new Type(id, name, weight, new ResourceLocation(GsonHelper.getAsString(json, "loot_table")), appearance,
                AirdropRules.conditions(json.has("conditions") ? GsonHelper.getAsJsonObject(json, "conditions") : new JsonObject()),
                AirdropRules.overrides(json.has("settings") ? GsonHelper.getAsJsonObject(json, "settings") : new JsonObject()));
    }

    public static boolean requirementsMet(JsonObject json) {
        var mods = AirdropRules.strings(json, "required_mods");
        for (String mod : mods) if (!mod.matches("[a-z][a-z0-9_]{1,63}")) throw new IllegalArgumentException("required_mods: invalid mod ID " + mod);
        return mods.stream().allMatch(net.minecraftforge.fml.ModList.get()::isLoaded);
    }

    public static java.util.List<String> validate(net.minecraft.server.MinecraftServer server) {
        var errors = new java.util.ArrayList<>(parseErrors);
        try {
            AirdropValidation.validateDimensions(server, com.prtsnote.airdrop.config.AirdropConfig.ALLOWED_DIMENSIONS.get(), "allowed_dimensions");
        } catch (RuntimeException error) {
            errors.add("serverconfig/airdrop_supply_drops-server.toml: " + error.getMessage());
        }
        Map<ResourceLocation, Type> valid = new LinkedHashMap<>();
        candidates.forEach((id, type) -> {
            try {
                type.settings().resolve();
                AirdropValidation.validate(server, type);
                valid.put(id, type);
            } catch (RuntimeException error) {
                errors.add("data/" + id.getNamespace() + "/airdrop_types/" + id.getPath() + ".json: " + error.getMessage());
            }
        });
        types = Map.copyOf(valid);
        diagnostics = java.util.List.copyOf(errors);
        return diagnostics;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Type> next = new LinkedHashMap<>();
        var errors = new java.util.ArrayList<String>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                if (!requirementsMet(json)) {
                    LogUtils.getLogger().info("Skipped optional airdrop type {}: required mod is absent", entry.getKey());
                    return;
                }
                next.put(entry.getKey(), parse(entry.getKey(), json));
            } catch (RuntimeException error) {
                errors.add("data/" + entry.getKey().getNamespace() + "/airdrop_types/" + entry.getKey().getPath() + ".json: " + error.getMessage());
                LogUtils.getLogger().error("Invalid airdrop type data/{}/airdrop_types/{}.json: {}",
                        entry.getKey().getNamespace(), entry.getKey().getPath(), error.getMessage());
            }
        });
        candidates = Map.copyOf(next);
        types = candidates;
        parseErrors = java.util.List.copyOf(errors);
        diagnostics = parseErrors;
        LogUtils.getLogger().info("Loaded {} airdrop types", types.size());
    }
}
