package com.prtsnote.airdrop.gametest;

import com.google.gson.JsonParser;
import com.prtsnote.airdrop.data.AirdropAppearance;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.world.block.AirdropCrateBlock;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropAppearanceGameTests {
    private static AirdropTypes.Type type(String appearance) {
        var json = JsonParser.parseString("""
                {"schema_version":1,"display_name":{"text":"Custom appearance"},"weight":1,
                 "loot_table":"airdrop_supply_drops:airdrop/mineral","appearance":"mineral"}
                """).getAsJsonObject();
        json.addProperty("appearance", appearance);
        return AirdropTypes.parse(ResourceLocation.parse("example_airdrops:military"), json);
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void appearanceIdsAndLegacyAliases(GameTestHelper h) {
        h.assertTrue(type("mineral").appearance().equals(type("airdrop_supply_drops:mineral").appearance()), "Legacy mineral alias must resolve identically");
        h.assertTrue(type("food").appearance().equals(AirdropAppearance.FOOD), "Legacy food alias must resolve identically");
        h.assertTrue(type("tacz_airdrop:military").appearance().toString().equals("tacz_airdrop:military"), "External appearance namespaces must be preserved");
        for (String bad : new String[]{"military", ":military", "tacz_airdrop:", "TACZ:military", "tacz:bad space", "#tacz:military"}) {
            try { type(bad); h.fail("Invalid appearance accepted: " + bad); }
            catch (IllegalArgumentException expected) { h.assertTrue(expected.getMessage().contains("appearance"), "Diagnostic must identify appearance"); }
        }
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void directCrateAppearanceSaveAndSync(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.assertTrue(AirdropServer.placeCrate(level, pos, type("tacz_airdrop:military")), "Unknown client asset must not reject server loot");
        var crate = (AirdropCrateBlockEntity) level.getBlockEntity(pos);
        var saved = crate.saveWithFullMetadata(level.registryAccess());
        var restored = new AirdropCrateBlockEntity(pos, crate.getBlockState()); restored.loadWithComponents(saved, level.registryAccess());
        h.assertTrue(restored.appearance().equals(crate.appearance()) && !restored.isEmpty(), "NBT must preserve ID and cargo");
        var update = crate.getUpdateTag(level.registryAccess());
        h.assertTrue(update.getString("appearance").equals("tacz_airdrop:military"), "Chunk-load update must include appearance ID");
        h.assertTrue(!update.contains("Items") && !update.contains("airdrop_settings"), "Visual update must not send inventories or server settings");
        var client = new AirdropCrateBlockEntity(pos, crate.getBlockState());
        client.handleUpdateTag(update, level.registryAccess());
        h.assertTrue(client.getModelData().get(AirdropAppearance.MODEL_PROPERTY).equals(crate.appearance()), "Received ID must reach chunk model data");
        h.assertTrue(crate.getUpdatePacket().getTag().getString("appearance").equals("tacz_airdrop:military"), "Block update packet must include appearance");
        saved.remove("appearance");
        var oldFood = new AirdropCrateBlockEntity(pos, ModBlocks.AIRDROP_CRATE.get().defaultBlockState().setValue(AirdropCrateBlock.FOOD, true));
        oldFood.loadWithComponents(saved, level.registryAccess());
        h.assertTrue(oldFood.appearance().equals(AirdropAppearance.FOOD), "Old landed food crates must migrate from block state");
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", timeoutTicks = 40)
    public static void fallingAppearanceSurvivesSaveAndLanding(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var original = ModEntities.FALLING_AIRDROP.get().create(level);
        original.setPos(pos.getX() + .5, pos.getY() + .6, pos.getZ() + .5);
        h.assertTrue(original.prepare(type("tacz_airdrop:military")), "Custom cargo must prepare");
        var frozen = original.saveWithoutId(new CompoundTag());
        var restored = ModEntities.FALLING_AIRDROP.get().create(level); restored.load(frozen);
        h.assertTrue(restored.appearance().toString().equals("tacz_airdrop:military"), "Saved entity ID must not consult a changed type definition");
        h.assertTrue(restored.getEntityData().getNonDefaultValues().stream().anyMatch(v -> "tacz_airdrop:military".equals(v.value())), "Spawn metadata must contain appearance ID");
        var legacy = frozen.copy(); legacy.remove("appearance"); legacy.putBoolean("food", true);
        var oldDrop = ModEntities.FALLING_AIRDROP.get().create(level); oldDrop.load(legacy);
        h.assertTrue(oldDrop.appearance().equals(AirdropAppearance.FOOD), "Old falling food crates must migrate");
        level.addFreshEntity(restored);
        h.runAfterDelay(20, () -> {
            h.assertTrue(restored.isRemoved() && level.getBlockEntity(pos) instanceof AirdropCrateBlockEntity, "Cargo must land");
            var landed = (AirdropCrateBlockEntity) level.getBlockEntity(pos);
            h.assertTrue(landed.appearance().toString().equals("tacz_airdrop:military") && !landed.isEmpty(), "Landing must preserve ID and contents");
            h.succeed();
        });
    }
}
