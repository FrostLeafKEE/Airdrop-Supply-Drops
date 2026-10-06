package com.prtsnote.airdrop;

import com.google.gson.JsonParser;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropRules;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.data.AirdropValidation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder("airdrop_supply_drops")
@PrefixGameTestTemplate(false)
public final class AirdropDimensionGameTests {
    @GameTest(template = "empty")
    public static void dimensionRulesKeepIdCompatibility(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        var rules = AirdropRules.conditions(JsonParser.parseString("""
                {"dimensions":["minecraft:the_nether","#example_airdrops:allowed_dimensions"]}
                """).getAsJsonObject());
        helper.assertTrue(rules.matches(level, pos), "A dimension ID or a dimension tag must match");
        helper.assertTrue(AirdropRules.matchesDimension(level, "minecraft:overworld"), "Existing explicit dimension IDs must work");
        helper.assertTrue(!AirdropRules.matchesDimension(level, "#missing:dimension_group"), "A missing group must never match");
        helper.assertTrue(AirdropRules.conditions(JsonParser.parseString("{\"dimensions\":[]}").getAsJsonObject()).matches(level, pos),
                "Empty type conditions must remain unrestricted");
        for (String invalid : List.of("#", "#missing_namespace", "##example:group", "example:bad space")) {
            rejects(helper, () -> AirdropRules.conditions(JsonParser.parseString("{\"dimensions\":[\"" + invalid + "\"]}").getAsJsonObject()),
                    "conditions.dimensions");
        }
        rejects(helper, () -> AirdropValidation.validateDimensions(level.getServer(), List.of("missing:dimension"), "conditions.dimensions"),
                "unknown dimension");
        rejects(helper, () -> AirdropValidation.validateDimensions(level.getServer(), List.of("#missing:dimension_group"), "conditions.dimensions"),
                "missing or empty dimension tag");
        var previous = AirdropConfig.ALLOWED_DIMENSIONS.get();
        try {
            AirdropConfig.ALLOWED_DIMENSIONS.set(List.of("#example_airdrops:allowed_dimensions"));
            helper.assertTrue(AirdropConfig.isDimensionAllowed(level), "The scheduler whitelist must accept the same dimension tags");
            AirdropConfig.ALLOWED_DIMENSIONS.set(List.of());
            helper.assertTrue(!AirdropConfig.isDimensionAllowed(level), "An empty global whitelist must still disable automatic events");
        } finally {
            AirdropConfig.ALLOWED_DIMENSIONS.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dimension_reload", timeoutTicks = 1200)
    public static void dimensionTagsAppendReplaceAndReload(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        var previousDimensions = AirdropConfig.ALLOWED_DIMENSIONS.get();
        var selected = List.copyOf(server.getPackRepository().getSelectedIds());
        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("airdrop_dimension_probe");
        helper.assertTrue(!Files.exists(pack), "The dedicated dimension probe path must be unused");
        var tagDir = pack.resolve("data/example_airdrops/tags/dimension");
        var typeDir = pack.resolve("data/example_airdrops/airdrop_types");
        Files.createDirectories(tagDir);
        Files.createDirectories(typeDir);
        var groupFile = tagDir.resolve("allowed_dimensions.json");
        var nestedFile = tagDir.resolve("dimension_bundle.json");
        var emptyFile = tagDir.resolve("empty_dimensions.json");
        var brokenFile = tagDir.resolve("broken_dimensions.json");
        var invalidFile = typeDir.resolve("_invalid_dimension_probe.json");
        Files.copy(pack.getParent().resolve("airdrop_example/pack.mcmeta"), pack.resolve("pack.mcmeta"));
        Files.writeString(groupFile, """
                {"replace":false,"values":["minecraft:the_end",{"id":"missing:optional_dimension","required":false}]}
                """);
        Files.writeString(nestedFile, "{\"values\":[\"#example_airdrops:allowed_dimensions\"]}");
        Files.writeString(emptyFile, "{\"values\":[]}");
        Files.writeString(brokenFile, "{\"values\":[\"minecraft:overworld\",\"missing:required_dimension\"]}");
        Files.writeString(invalidFile, """
                {"schema_version":1,"display_name":{"text":"Invalid dimension probe"},"weight":1,
                 "loot_table":"airdrop_supply_drops:airdrop/food","appearance":"food",
                 "conditions":{"dimensions":["#missing:dimension_probe"]}}
                """);
        server.getPackRepository().reload();
        var withProbe = new ArrayList<>(selected);
        withProbe.add("file/airdrop_dimension_probe");
        server.reloadResources(withProbe).thenRunAsync(() -> {
            var overworld = server.overworld();
            var end = server.getLevel(Level.END);
            var nether = server.getLevel(Level.NETHER);
            helper.assertTrue(AirdropRules.matchesDimension(overworld, "#example_airdrops:dimension_bundle")
                    && AirdropRules.matchesDimension(end, "#example_airdrops:dimension_bundle")
                    && !AirdropRules.matchesDimension(nether, "#example_airdrops:dimension_bundle"),
                    "A higher pack must append IDs and resolve nested tags, ignoring absent optional IDs");
            var survival = AirdropTypes.all().get(new ResourceLocation("example_airdrops:survival"));
            helper.assertTrue(survival != null && survival.conditions().matches(end, BlockPos.ZERO), "Reloaded type conditions must use the merged tag");
            helper.assertTrue(!AirdropTypes.all().containsKey(new ResourceLocation("example_airdrops:_invalid_dimension_probe"))
                    && AirdropTypes.diagnostics().stream().anyMatch(error -> error.contains("_invalid_dimension_probe.json")
                    && error.contains("#missing:dimension_probe")), "Invalid dimension-tag references must exclude the type and identify its file");
            rejects(helper, () -> AirdropValidation.validateDimensions(server, List.of("#example_airdrops:empty_dimensions"), "conditions.dimensions"),
                    "missing or empty dimension tag");
            rejects(helper, () -> AirdropValidation.validateDimensions(server, List.of("#example_airdrops:broken_dimensions"), "conditions.dimensions"),
                    "missing or empty dimension tag");
            helper.assertTrue(!AirdropRules.matchesDimension(overworld, "#example_airdrops:broken_dimensions"),
                    "A tag with a missing required ID must fail entirely, even when another member is valid");
            AirdropConfig.ALLOWED_DIMENSIONS.set(List.of("#missing:whitelist_probe"));
            helper.assertTrue(AirdropTypes.validate(server).stream().anyMatch(error -> error.contains("serverconfig/")
                    && error.contains("allowed_dimensions") && error.contains("#missing:whitelist_probe")),
                    "Whitelist tag mistakes must be included in administrator validation diagnostics");
            AirdropConfig.ALLOWED_DIMENSIONS.set(List.of("#example_airdrops:dimension_bundle"));
            helper.assertTrue(AirdropConfig.isDimensionAllowed(end) && !AirdropConfig.isDimensionAllowed(nether), "Automatic eligibility must follow the merged group");
            try {
                Files.writeString(groupFile, "{\"replace\":true,\"values\":[\"minecraft:the_nether\"]}");
                Files.delete(invalidFile);
            } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        }, server).thenCompose(unused -> server.reloadResources(withProbe)).thenRunAsync(() -> {
            helper.assertTrue(AirdropConfig.isDimensionAllowed(server.getLevel(Level.NETHER))
                    && !AirdropConfig.isDimensionAllowed(server.overworld()) && !AirdropConfig.isDimensionAllowed(server.getLevel(Level.END)),
                    "replace=true must discard lower-pack IDs and update the whitelist without restarting");
            var survival = AirdropTypes.all().get(new ResourceLocation("example_airdrops:survival"));
            helper.assertTrue(survival.conditions().matches(server.getLevel(Level.NETHER), BlockPos.ZERO)
                    && !survival.conditions().matches(server.overworld(), BlockPos.ZERO), "All types sharing a group must use its new members after reload");
            helper.assertTrue(AirdropTypes.diagnostics().isEmpty(), "Corrected references must clear validation diagnostics");
        }, server).handleAsync((unused, error) -> {
            AirdropConfig.ALLOWED_DIMENSIONS.set(previousDimensions);
            try {
                for (var file : List.of(groupFile, nestedFile, emptyFile, brokenFile, invalidFile, pack.resolve("pack.mcmeta"))) Files.deleteIfExists(file);
                for (var directory : List.of(tagDir, tagDir.getParent(), typeDir, typeDir.getParent(), pack.resolve("data"), pack)) Files.deleteIfExists(directory);
            } catch (IOException cleanup) { throw new java.io.UncheckedIOException(cleanup); }
            server.getPackRepository().reload();
            return error;
        }, server).thenCompose(error -> server.reloadResources(selected).thenRunAsync(() -> {
            if (error != null) helper.fail("Dimension reload test failed: " + error);
            helper.assertTrue(AirdropRules.matchesDimension(server.overworld(), "#example_airdrops:allowed_dimensions")
                    && !AirdropRules.matchesDimension(server.getLevel(Level.NETHER), "#example_airdrops:allowed_dimensions"),
                    "Removing the append pack must restore the base group");
            helper.assertTrue(AirdropTypes.diagnostics().isEmpty(), "The restored data must be valid");
            helper.succeed();
        }, server)).exceptionally(error -> {
            server.execute(() -> helper.fail("Dimension reload test failed: " + error));
            return null;
        });
    }

    private static void rejects(GameTestHelper helper, Runnable operation, String expected) {
        try { operation.run(); }
        catch (IllegalArgumentException error) {
            helper.assertTrue(error.getMessage().contains(expected), "The diagnostic must contain " + expected + ": " + error.getMessage());
            return;
        }
        helper.fail("Expected rejection containing " + expected);
    }
}
