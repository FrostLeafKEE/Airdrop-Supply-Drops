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
            .comment("Minimum automatic event interval in seconds.")
            .defineInRange("interval_min_seconds", 1200, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue INTERVAL_MAX_SECONDS = BUILDER
            .comment("Maximum automatic event interval in seconds.")
            .defineInRange("interval_max_seconds", 1800, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MIN_DROP_DISTANCE = BUILDER
            .comment("Minimum horizontal distance from the selected player to the drop point.")
            .defineInRange("min_drop_distance", 64, 0, 200);

    public static final ForgeConfigSpec.IntValue MAX_DROP_DISTANCE = BUILDER
            .comment("Maximum horizontal distance from the selected player to the drop point.")
            .defineInRange("max_drop_distance", 200, 0, 200);

    public static final ForgeConfigSpec.IntValue LANDED_LIFETIME_SECONDS = BUILDER
            .comment("How long a landed, non-empty airdrop remains in seconds.")
            .defineInRange("landed_lifetime_seconds", 300, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.BooleanValue RESET_ON_REJOIN = BUILDER
            .comment("Reset landed crate timers when starting a new singleplayer session.")
            .define("reset_on_rejoin", true);

    public static final ForgeConfigSpec.BooleanValue ALLOW_LIQUID_LANDING = BUILDER
            .comment("Allow crates to land above liquid surfaces without replacing the liquid.")
            .define("allow_liquid_landing", true);

    public static final ForgeConfigSpec.ConfigValue<String> SMOKE_COLOR = BUILDER
            .comment("Smoke color for all supply crates, as a hexadecimal RGB color (#RRGGBB). Sent by the server with each particle.")
            .define("smoke_color", "#FF0000", value -> value instanceof String color && color.matches("#[0-9a-fA-F]{6}"));

    public static final ForgeConfigSpec.BooleanValue AIRBORNE_SMOKE_ENABLED = BUILDER
            .comment("Emit smoke while a supply crate is descending. Landed crates always emit smoke until removed.")
            .define("airborne_smoke_enabled", false);

    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_EVENTS = BUILDER
            .comment("Maximum number of active events across the server.")
            .defineInRange("max_active_events", 1, 1, 64);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS = BUILDER
            .comment("Dimension IDs or #dimension tags where automatic airdrops may be scheduled. Tags group dimension IDs, not dimension types.")
            .defineListAllowEmpty(
                    "allowed_dimensions",
                    List.of("minecraft:overworld"),
                    value -> value instanceof String && ((String) value).matches("#?[a-z0-9_.-]+:[a-z0-9/._-]+"));

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
        if (event.getConfig().getSpec() == SPEC) {
            validateRelationships();
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(() -> {
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
