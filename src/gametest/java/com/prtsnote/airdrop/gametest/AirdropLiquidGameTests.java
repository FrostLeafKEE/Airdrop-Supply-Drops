package com.prtsnote.airdrop.gametest;

import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.UUID;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropLiquidGameTests {
    @GameTest(template = "airdrop_supply_drops:empty")
    public static void waterBelowCreatesCrateWithoutReplacingWater(GameTestHelper helper) {
        liquidLanding(helper, Blocks.WATER, false);
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void submergedLavaDropSurfacesWithoutReplacingLava(GameTestHelper helper) {
        liquidLanding(helper, Blocks.LAVA, true);
    }

    private static void liquidLanding(GameTestHelper helper, Block liquid, boolean submerged) {
        var level = helper.getLevel();
        var base = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos pos = new BlockPos(base.getX(), 180, base.getZ());
        for (var direction : net.minecraft.core.Direction.values()) {
            if (direction != net.minecraft.core.Direction.UP) level.setBlockAndUpdate(pos.relative(direction), Blocks.GLASS.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, liquid.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
        var drop = ModEntities.FALLING_AIRDROP.get().create(level);
        drop.setPos(pos.getX() + 0.5, pos.getY() + (submerged ? 0.4 : 1.8), pos.getZ() + 0.5);
        helper.assertTrue(drop.prepare(AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:mineral"))), "Drop must prepare");
        level.addFreshEntity(drop);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(drop.isRemoved(), "Fluid below must trigger conversion before ground collision");
            helper.assertTrue(level.getBlockState(pos).is(liquid), "Original liquid must remain");
            helper.assertTrue(level.getBlockEntity(pos.above()) instanceof AirdropCrateBlockEntity crate && !crate.isEmpty(),
                    "Crate with inventory must appear above liquid");
            level.removeBlock(pos.above(), false);
            helper.succeed();
        });
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void completedLandedEventRetiresWithoutLoadingChunk(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        BlockPos remote = new BlockPos(2000000, 180, 2000000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "Test target must be unloaded");
        UUID id = UUID.randomUUID();
        CompoundTag event = new CompoundTag();
        event.putUUID("id", id); event.putUUID("plane_id", UUID.randomUUID());
        event.putString("dimension", helper.getLevel().dimension().location().toString());
        event.putLong("ground", remote.asLong()); event.putLong("deadline", AirdropEvents.now(server) - 1);
        event.putLong("started", AirdropEvents.now(server) - AirdropEvents.DEPARTURE_TICK);
        event.putString("stage", "LANDED");
        CompoundTag cargo = new CompoundTag(); cargo.putUUID("UUID", UUID.randomUUID()); event.put("cargo", cargo);
        ListTag list = new ListTag(); list.add(event);
        CompoundTag saved = new CompoundTag(); saved.put("events", list); saved.putInt("flight_path_version", AirdropEvents.FLIGHT_PATH_VERSION);
        var manager = AirdropEvents.load(saved, server.registryAccess());
        helper.assertTrue(manager.all().size() == 1, "Saved landed event must load");
        manager.tick(server);
        helper.assertTrue(manager.find(id) == null && manager.all().isEmpty(), "Completed events must retire independently of crate lifetime");
        helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "Event retirement must not load the distant crate chunk");
        helper.succeed();
    }
}
