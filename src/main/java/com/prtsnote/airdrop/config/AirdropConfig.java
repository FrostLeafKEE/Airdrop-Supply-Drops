package com.prtsnote.airdrop.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

public final class AirdropConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Whether automatic airdrop scheduling is enabled.")
            .define("enabled", true);

    public static final ForgeConfigSpec.IntValue INTERVAL_MIN_SECONDS = BUILDER
            .comment("Minimum interval between automatic delivery starts in seconds. Only one aircraft may be in flight; landed crates do not count. Changing either interval resets the next check.")
            .defineInRange("interval_min_seconds", 1200, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue INTERVAL_MAX_SECONDS = BUILDER
            .comment("Maximum automatic event interval in seconds. Set equal to interval_min_seconds for a fixed interval.")
            .defineInRange("interval_max_seconds", 1800, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MIN_DROP_DISTANCE = BUILDER
            .comment("Minimum horizontal distance from the selected player to the drop point.")
            .defineInRange("min_drop_distance", 64, 0, 200);

    public static final ForgeConfigSpec.IntValue MAX_DROP_DISTANCE = BUILDER
            .comment("Maximum horizontal distance from the selected player to the drop point.")
            .defineInRange("max_drop_distance", 150, 0, 200);

    public static final ForgeConfigSpec.BooleanValue ALLOW_LIQUID_LANDING = BUILDER
            .comment("Allow crates to land above liquid surfaces without replacing the liquid.")
            .define("allow_liquid_landing", true);

    public static final ForgeConfigSpec.ConfigValue<String> SMOKE_COLOR = BUILDER
            .comment("Smoke color for all supply crates, as a hexadecimal RGB color (#RRGGBB). Sent by the server with each particle.")
            .define("smoke_color", "#FF0000", value -> value instanceof String color && color.matches("#[0-9a-fA-F]{6}"));

    public static final ForgeConfigSpec.BooleanValue AIRBORNE_SMOKE_ENABLED = BUILDER
            .comment("Emit smoke while a supply crate is descending. Landed crates emit smoke while they contain supplies.")
            .define("airborne_smoke_enabled", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS = BUILDER
            .comment("Dimension IDs or #dimension tags where automatic airdrops and signal tubes may request deliveries. Tags group dimension IDs, not dimension types.")
            .defineListAllowEmpty(
                    "allowed_dimensions",
                    List.of("minecraft:overworld"),
                    value -> value instanceof String && ((String) value).matches("#?[a-z0-9_.-]+:[a-z0-9/._-]+"));

    static { BUILDER.push("signal_tube"); }
    public static final ForgeConfigSpec.DoubleValue SIGNAL_LOOT_CHANCE = BUILDER
            .comment("Chance to add one signal tube to a vanilla structure chest loot roll. Zero disables this bonus.")
            .defineInRange("loot_chance", 0.05, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue SIGNAL_RANDOM_CHANCE = BUILDER
            .comment("Fraction of bonus signal tubes that have no suffix and request a random type. Other tubes bind a loaded type by its weight.")
            .defineInRange("random_variant_chance", 0.30, 0.0, 1.0);
    public static final ForgeConfigSpec.DoubleValue SIGNAL_RANGE = BUILDER
            .comment("Maximum flare flight distance in blocks before bursting. Collisions burst sooner.")
            .defineInRange("range", 48.0, 1.0, 256.0);
    public static final ForgeConfigSpec.DoubleValue SIGNAL_SPEED = BUILDER
            .comment("Flare speed in blocks per tick, along the player's aim.")
            .defineInRange("speed", 1.5, 0.25, 4.0);
    public static final ForgeConfigSpec.DoubleValue SIGNAL_RADIUS = BUILDER
            .comment("Burst damage radius in blocks. The burst does not break blocks or create ground fire.")
            .defineInRange("burst_radius", 3.0, 0.1, 8.0);
    public static final ForgeConfigSpec.DoubleValue SIGNAL_DAMAGE = BUILDER
            .comment("Raw burst damage before armour and other damage modifiers; 3 points are 1.5 hearts.")
            .defineInRange("damage", 3.0, 0.0, 100.0);
    public static final ForgeConfigSpec.IntValue SIGNAL_BURN_SECONDS = BUILDER
            .comment("Fire duration on a directly hit living entity. Nearby burst victims are not ignited.")
            .defineInRange("direct_hit_burn_seconds", 5, 0, 60);
    static { BUILDER.pop(); }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private AirdropConfig() {
    }

    public static int smokeColor() {
        return Integer.parseInt(SMOKE_COLOR.get().substring(1), 16);
    }

    public static boolean isDimensionAllowed(net.minecraft.server.level.ServerLevel level) {
        return ALLOWED_DIMENSIONS.get().stream()
                .anyMatch(rule -> com.prtsnote.airdrop.data.AirdropRules.matchesDimension(level, rule));
    }

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            validateRelationships();
        }
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC && SPEC.isLoaded()) {
            validateRelationships();
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.overworld() != null) server.execute(() -> {
                for (String error : com.prtsnote.airdrop.data.AirdropTypes.validate(server)) {
                    com.mojang.logging.LogUtils.getLogger().error("Invalid airdrop configuration: {}", error);
                }
            });
        }
    }

    public static void validateRelationships() {
        if (INTERVAL_MIN_SECONDS.get() > INTERVAL_MAX_SECONDS.get()) {
            throw new IllegalStateException("airdrop interval_min_seconds cannot exceed interval_max_seconds");
        }
        if (MIN_DROP_DISTANCE.get() > MAX_DROP_DISTANCE.get()) {
            throw new IllegalStateException("airdrop min_drop_distance cannot exceed max_drop_distance");
        }
    }
}
