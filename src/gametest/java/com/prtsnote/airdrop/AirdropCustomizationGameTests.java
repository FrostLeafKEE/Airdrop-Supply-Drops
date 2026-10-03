package com.prtsnote.airdrop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.prtsnote.airdrop.data.AirdropRules;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.data.AirdropValidation;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("airdrop_supply_drops")
@PrefixGameTestTemplate(false)
public final class AirdropCustomizationGameTests {
    @GameTest(template = "empty", batch = "reload", timeoutTicks = 1200)
    public static void realDatapackReloadPreservesCargo(GameTestHelper helper) throws java.io.IOException {
        var server = helper.getLevel().getServer();
        var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR)
                .resolve("airdrop_example/data/example_airdrops/airdrop_types");
        var file = directory.resolve("_reload_test.json");
        var invalid = directory.resolve("_invalid_test.json");
        helper.assertTrue(java.nio.file.Files.isDirectory(directory) && !java.nio.file.Files.exists(file) && !java.nio.file.Files.exists(invalid),
                "Dedicated example test pack and unused probe paths are required");
        var selected = java.util.List.copyOf(server.getPackRepository().getSelectedIds());
        var json = definition(); json.add("settings", JsonParser.parseString("{\"landed_lifetime_seconds\":2}"));
        java.nio.file.Files.writeString(file, json.toString());
        var frozen = new java.util.concurrent.atomic.AtomicReference<CompoundTag>();
        server.reloadResources(selected).thenRunAsync(() -> {
            var type = AirdropTypes.all().get(new ResourceLocation("example_airdrops:_reload_test"));
            helper.assertTrue(type != null && type.settings().resolve().lifetimeSeconds() == 2, "Reload must read the new type file");
            var drop = ModEntities.FALLING_AIRDROP.get().create(helper.getLevel());
            helper.assertTrue(drop.prepare(type), "Reloaded type must generate cargo");
            frozen.set(drop.saveWithoutId(new CompoundTag()));
            json.add("settings", JsonParser.parseString("{\"landed_lifetime_seconds\":900}"));
            var bad = definition(); bad.addProperty("loot_table", "missing:reload_probe");
            try {
                java.nio.file.Files.writeString(file, json.toString());
                java.nio.file.Files.writeString(invalid, bad.toString());
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }, server).thenCompose(unused -> server.reloadResources(selected)).thenRunAsync(() -> {
            var type = AirdropTypes.all().get(new ResourceLocation("example_airdrops:_reload_test"));
            helper.assertTrue(type.settings().resolve().lifetimeSeconds() == 900, "Changed settings must apply to new drops");
            helper.assertTrue(!AirdropTypes.all().containsKey(new ResourceLocation("example_airdrops:_invalid_test")), "Post-reload validation must exclude invalid references");
            helper.assertTrue(AirdropTypes.diagnostics().stream().anyMatch(error -> error.contains("_invalid_test.json") && error.contains("missing:reload_probe")),
                    "Post-reload diagnostic must identify the source file and missing table");
            var restored = ModEntities.FALLING_AIRDROP.get().create(helper.getLevel()); restored.load(frozen.get());
            helper.assertTrue(restored.saveWithoutId(new CompoundTag()).getCompound("airdrop_settings").getInt("lifetime_seconds") == 2,
                    "Existing cargo must retain its settings after a real reload");
        }, server).handleAsync((unused, error) -> {
            try {
                java.nio.file.Files.deleteIfExists(file);
                java.nio.file.Files.deleteIfExists(invalid);
            } catch (java.io.IOException cleanup) { throw new java.io.UncheckedIOException(cleanup); }
            return error;
        }, server).thenCompose(error -> server.reloadResources(selected).thenRunAsync(() -> {
            if (error != null) helper.fail("Reload test failed: " + error);
            helper.assertTrue(AirdropTypes.diagnostics().isEmpty(), "Restored pack must have no validation errors");
            helper.succeed();
        }, server)).exceptionally(error -> {
            server.execute(() -> helper.fail("Reload test failed: " + error));
            return null;
        });
    }

    private static JsonObject definition() {
        return JsonParser.parseString("""
                {"schema_version":1,"display_name":{"text":"Test"},"weight":1,
                 "loot_table":"airdrop_supply_drops:airdrop/mineral","appearance":"mineral"}
                """).getAsJsonObject();
    }
    private static AirdropTypes.Type parse(JsonObject json) { return AirdropTypes.parse(new ResourceLocation("airdrop_supply_drops:test"), json); }
    private static void rejects(GameTestHelper helper, Runnable action, String field) {
        try { action.run(); } catch (RuntimeException error) {
            helper.assertTrue(error.getMessage().contains(field), "Diagnostic must identify " + field + ": " + error.getMessage());
            return;
        }
        helper.fail("Expected rejection for " + field);
    }

    @GameTest(template = "empty")
    public static void legacyDefaultsAndInvalidOverrides(GameTestHelper helper) {
        var old = parse(definition());
        helper.assertTrue(old.settings().resolve().equals(AirdropRules.defaults()), "Version 1 files must inherit defaults");
        helper.assertTrue(old.conditions().matches(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)), "Absent conditions must allow all environments");
        for (String text : new String[]{"{\"landed_lifetime_seconds\":0}", "{\"max_drop_distance\":201}",
                "{\"reset_on_rejoin\":\"false\"}", "{\"min_drop_distance\":100,\"max_drop_distance\":80}", "{\"typo\":true}"}) {
            var json = definition(); json.add("settings", JsonParser.parseString(text));
            rejects(helper, () -> parse(json), "settings.");
        }
        var partial = definition(); partial.add("settings", JsonParser.parseString("{\"max_drop_distance\":1}"));
        rejects(helper, () -> parse(partial).settings().resolve(), "after inheriting");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void conditionsAndOptionalMods(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(BlockPos.ZERO);
        var biome = level.getBiome(pos).unwrapKey().orElseThrow().location().toString();
        var dimension = level.dimension().location().toString();
        var conditions = new AirdropRules.Conditions(java.util.List.of(dimension), java.util.List.of(biome), "any", "any");
        helper.assertTrue(conditions.matches(level, pos), "Exact biome and dimension must match");
        helper.assertTrue(!new AirdropRules.Conditions(java.util.List.of("missing:dimension"), java.util.List.of(), "any", "any").matches(level, pos), "Wrong dimension must fail");
        helper.assertTrue(!new AirdropRules.Conditions(java.util.List.of(), java.util.List.of("missing:biome"), "any", "any").matches(level, pos), "Wrong biome must fail");
        helper.assertTrue(new AirdropRules.Conditions(java.util.List.of(), java.util.List.of("#minecraft:is_overworld"), "any", "any").matches(level, pos), "Biome tags must match");
        var day = new AirdropRules.Conditions(java.util.List.of(), java.util.List.of(), "clear", "day");
        helper.assertTrue(day.matchesTime(0) && day.matchesTime(11999) && !day.matchesTime(12000) && day.matchesTime(24000), "Day boundaries must be exact");
        helper.assertTrue(day.matchesWeather(false, false) && !day.matchesWeather(true, false), "Clear weather must reject rain");
        var night = new AirdropRules.Conditions(java.util.List.of(), java.util.List.of(), "thunder", "night");
        helper.assertTrue(night.matchesTime(12000) && !night.matchesTime(0) && night.matchesWeather(true, true) && !night.matchesWeather(true, false), "Night and thunder rules must match");
        helper.assertTrue(AirdropTypes.requirementsMet(JsonParser.parseString("{\"required_mods\":[\"airdrop_supply_drops\"]}").getAsJsonObject()), "Installed mod must be accepted");
        helper.assertTrue(!AirdropTypes.requirementsMet(JsonParser.parseString("{\"required_mods\":[\"missing_test_dependency\"]}").getAsJsonObject()), "Absent optional mod must skip type");
        var bad = definition(); bad.add("conditions", JsonParser.parseString("{\"weather\":\"snow\"}"));
        rejects(helper, () -> parse(bad), "conditions.weather");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void referenceValidation(GameTestHelper helper) {
        helper.assertTrue(AirdropTypes.validate(helper.getLevel().getServer()).isEmpty(), "Built-in and example references must validate");
        var json = definition(); json.addProperty("loot_table", "missing:table");
        rejects(helper, () -> AirdropValidation.validate(helper.getLevel().getServer(), parse(json)), "loot_table");
        json.addProperty("loot_table", "airdrop_supply_drops:airdrop/mineral");
        json.add("conditions", JsonParser.parseString("{\"biomes\":[\"missing:biome\"]}"));
        rejects(helper, () -> AirdropValidation.validate(helper.getLevel().getServer(), parse(json)), "conditions.biomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void customLifetimeAndCargoSnapshot(GameTestHelper helper) {
        var json = definition(); json.add("settings", JsonParser.parseString("{\"landed_lifetime_seconds\":2,\"reset_on_rejoin\":false}"));
        var type = parse(json);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(AirdropServer.placeCrate(helper.getLevel(), pos, type), "Customized crate must place");
        var crate = (AirdropCrateBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(crate.getRemainingTicks() == 40, "Crate must inherit type lifetime");
        var saved = crate.saveWithFullMetadata();
        var drop = ModEntities.FALLING_AIRDROP.get().create(helper.getLevel()); drop.setPos(Vec3.atCenterOf(pos.above(8)));
        helper.assertTrue(drop.prepare(type), "Cargo must prepare");
        var cargo = drop.saveWithoutId(new CompoundTag());
        json.add("settings", JsonParser.parseString("{\"landed_lifetime_seconds\":999}"));
        helper.assertTrue(parse(json).settings().resolve().lifetimeSeconds() == 999, "Changed definition must affect future events");
        var restored = ModEntities.FALLING_AIRDROP.get().create(helper.getLevel()); restored.load(cargo);
        helper.assertTrue(restored.saveWithoutId(new CompoundTag()).getCompound("airdrop_settings").getInt("lifetime_seconds") == 2,
                "Saved cargo must not consult changed definitions");
        helper.runAfterDelay(5, () -> {
            crate.load(saved); crate.onLoad();
            helper.assertTrue(crate.getRemainingTicks() <= 35, "NBT reload must preserve custom deadline");
            helper.assertTrue(!crate.saveWithFullMetadata().getCompound("airdrop_settings").getBoolean("reset_on_rejoin"), "Reset opt-out must persist");
        });
        helper.runAfterDelay(42, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) == null, "Custom two-second crate must expire");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void independentOnePercentBonus(GameTestHelper helper) {
        var table = helper.getLevel().getServer().getLootData().getLootTable(new ResourceLocation("example_airdrops:airdrop/survival"));
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        int successes = 0;
        for (int seed = 1; seed <= 10000; seed++) {
            int diamonds = table.getRandomItems(params, seed).stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            helper.assertTrue(diamonds <= 1, "Independent bonus must never produce more than one diamond per crate");
            successes += diamonds;
        }
        helper.assertTrue(successes >= 60 && successes <= 150, "Ten thousand crates must approximate one-percent bonus; actual=" + successes);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disallowedLiquidCancelsDrop(GameTestHelper helper) {
        var json = definition(); json.add("settings", JsonParser.parseString("{\"allow_liquid_landing\":false}"));
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.WATER.defaultBlockState());
        helper.assertTrue(!AirdropServer.placeCrate(helper.getLevel(), pos, parse(json)), "Direct placement must reject liquid support");
        var drop = ModEntities.FALLING_AIRDROP.get().create(helper.getLevel());
        drop.setPos(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5); drop.prepare(parse(json));
        helper.getLevel().addFreshEntity(drop);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(drop.isRemoved() && helper.getLevel().getBlockEntity(pos) == null, "Unexpected liquid must cancel the cargo");
            helper.assertTrue(!helper.getLevel().getFluidState(pos.below()).isEmpty(), "Liquid must remain unchanged");
            helper.succeed();
        });
    }
}
