package com.prtsnote.airdrop.data;

import com.google.gson.JsonObject;
import com.prtsnote.airdrop.config.AirdropConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Set;

/** Optional datapack rules; resolved settings are saved with the cargo. */
public final class AirdropRules {
    public record Conditions(List<String> dimensions, List<String> biomes, String weather, String time) {
        public boolean matches(ServerLevel level, BlockPos pos) {
            if (!dimensions.isEmpty() && dimensions.stream().noneMatch(rule -> matchesDimension(level, rule))) return false;
            var biome = level.getBiome(pos);
            if (!biomes.isEmpty() && biomes.stream().noneMatch(id -> id.startsWith("#")
                    ? biome.is(TagKey.create(Registries.BIOME, new ResourceLocation(id.substring(1))))
                    : biome.is(new ResourceLocation(id)))) return false;
            if (!matchesWeather(level.isRaining(), level.isThundering())) return false;
            return matchesTime(level.getDayTime());
        }

        public boolean matchesWeather(boolean raining, boolean thundering) {
            return switch (weather) {
                case "clear" -> !raining && !thundering;
                case "rain" -> raining;
                case "thunder" -> thundering;
                default -> true;
            };
        }

        public boolean matchesTime(long dayTime) {
            long tick = Math.floorMod(dayTime, 24000L);
            return time.equals("any") || (time.equals("day") ? tick < 12000 : tick >= 12000);
        }
    }

    /** Dimension tags contain level-stem IDs, never dimension-type IDs. */
    public static boolean matchesDimension(ServerLevel level, String rule) {
        if (!rule.startsWith("#")) return rule.equals(level.dimension().location().toString());
        var dimensions = level.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        var key = ResourceKey.create(Registries.LEVEL_STEM, level.dimension().location());
        var tag = TagKey.create(Registries.LEVEL_STEM, new ResourceLocation(rule.substring(1)));
        return dimensions.getHolder(key).map(holder -> holder.is(tag)).orElse(false);
    }

    public record Overrides(Integer minDistance, Integer maxDistance, Boolean allowLiquidLanding) {
        public Settings resolve() {
            Settings settings = new Settings(minDistance == null ? AirdropConfig.MIN_DROP_DISTANCE.get() : minDistance,
                    maxDistance == null ? AirdropConfig.MAX_DROP_DISTANCE.get() : maxDistance,
                    allowLiquidLanding == null ? AirdropConfig.ALLOW_LIQUID_LANDING.get() : allowLiquidLanding);
            if (settings.minDistance > settings.maxDistance) {
                throw new IllegalArgumentException("settings.min_drop_distance exceeds max_drop_distance after inheriting server defaults");
            }
            return settings;
        }
    }

    public record Settings(int minDistance, int maxDistance, boolean allowLiquidLanding) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("min_distance", minDistance);
            tag.putInt("max_distance", maxDistance);
            tag.putBoolean("allow_liquid_landing", allowLiquidLanding);
            return tag;
        }

        public static Settings load(CompoundTag tag) {
            if (!tag.contains("min_distance") || !tag.contains("max_distance")) return defaults();
            return new Settings(tag.getInt("min_distance"), tag.getInt("max_distance"), tag.getBoolean("allow_liquid_landing"));
        }
    }

    public static Settings defaults() { return new Overrides(null, null, null).resolve(); }

    public static Conditions conditions(JsonObject json) {
        fields(json, Set.of("dimensions", "biomes", "weather", "time"), "conditions");
        var dimensions = ids(json, "dimensions", true);
        var biomes = ids(json, "biomes", true);
        String weather = option(json, "weather", Set.of("any", "clear", "rain", "thunder"));
        String time = option(json, "time", Set.of("any", "day", "night"));
        return new Conditions(dimensions, biomes, weather, time);
    }

    public static Overrides overrides(JsonObject json) {
        fields(json, Set.of("min_drop_distance", "max_drop_distance", "landed_lifetime_seconds",
                "reset_on_rejoin", "allow_liquid_landing"), "settings");
        Integer min = optionalInt(json, "min_drop_distance", 0, 200);
        Integer max = optionalInt(json, "max_drop_distance", 0, 200);
        if (min != null && max != null && min > max) throw new IllegalArgumentException("settings.min_drop_distance exceeds max_drop_distance");
        // Accept legacy fields so existing datapacks still load; they no longer limit crate lifetime.
        optionalInt(json, "landed_lifetime_seconds", 1, Integer.MAX_VALUE);
        optionalBoolean(json, "reset_on_rejoin");
        return new Overrides(min, max, optionalBoolean(json, "allow_liquid_landing"));
    }

    public static void fields(JsonObject json, Set<String> allowed, String path) {
        for (String key : json.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException(path + "." + key + " is unknown");
    }

    public static List<String> strings(JsonObject json, String name) {
        if (!json.has(name)) return List.of();
        if (!json.get(name).isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        var values = new java.util.ArrayList<String>();
        for (var value : json.getAsJsonArray(name)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(name + " must contain strings");
            values.add(value.getAsString());
        }
        return List.copyOf(values);
    }

    private static List<String> ids(JsonObject json, String name, boolean tags) {
        var values = strings(json, name);
        for (String value : values) {
            String id = tags && value.startsWith("#") ? value.substring(1) : value;
            if (!id.contains(":") || ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("conditions." + name + ": invalid ID " + value);
        }
        return values;
    }

    private static String option(JsonObject json, String name, Set<String> values) {
        if (!json.has(name)) return "any";
        if (!json.get(name).isJsonPrimitive() || !json.getAsJsonPrimitive(name).isString()
                || !values.contains(json.get(name).getAsString())) throw new IllegalArgumentException("conditions." + name + " must be one of " + values);
        return json.get(name).getAsString();
    }

    private static Integer optionalInt(JsonObject json, String name, int min, int max) {
        if (!json.has(name)) return null;
        int value = AirdropTypes.integer(json, name);
        if (value < min || value > max) throw new IllegalArgumentException("settings." + name + " must be " + min + ".." + max);
        return value;
    }

    private static Boolean optionalBoolean(JsonObject json, String name) {
        if (!json.has(name)) return null;
        if (!json.get(name).isJsonPrimitive() || !json.getAsJsonPrimitive(name).isBoolean()) throw new IllegalArgumentException("settings." + name + " must be boolean");
        return json.get(name).getAsBoolean();
    }
}
