package com.prtsnote.airdrop.gametest;

import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.data.AirdropValidation;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.registry.ModItems;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.item.SignalTubeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("airdrop_supply_drops")
public final class SignalTubeGameTests {
    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void bindingSurvivesStorageAndAnvilName(GameTestHelper h) {
        var type = AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:food"));
        var stack = SignalTubeItem.boundStack(type);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("mineral"));
        var saved = stack.save(h.getLevel().registryAccess());
        var restored = ItemStack.parse(h.getLevel().registryAccess(), saved).orElseThrow();
        h.assertTrue(type.id().equals(SignalTubeItem.binding(restored)), "Renaming or saving cannot retarget a bound tube");
        h.assertTrue(ModItems.SIGNAL_TUBE.get().getName(restored).equals(Component.translatable("item.airdrop_supply_drops.signal_tube.typed", type.name())), "Suffix must preserve the type label component");
        h.assertTrue(!SignalTubeItem.hasBinding(SignalTubeItem.randomStack()), "Random tube must remain unsuffixed");
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal", timeoutTicks = 100)
    public static void vanillaChestBonusIsSeededAndKeepsOriginalLoot(GameTestHelper h) {
        var table = AirdropValidation.lootTable(h.getLevel().getServer(), ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"));
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        int total = 0, random = 0;
        for (int seed = 1; seed <= 3000; seed++) {
            var loot = table.getRandomItems(params, seed);
            h.assertTrue(loot.stream().anyMatch(s -> !s.is(ModItems.SIGNAL_TUBE.get())), "Bonus must preserve vanilla supplies");
            for (var stack : loot) if (stack.is(ModItems.SIGNAL_TUBE.get())) {
                total++;
                if (!SignalTubeItem.hasBinding(stack)) random++;
                else h.assertTrue(AirdropTypes.all().containsKey(SignalTubeItem.binding(stack)), "Bound loot must select a loaded type");
            }
            if (seed <= 200) {
                var again = table.getRandomItems(params, seed);
                h.assertTrue(loot.size() == again.size(), "Seeded roll must reproduce its bonus chance");
                for (int i = 0; i < loot.size(); i++) h.assertTrue(ItemStack.matches(loot.get(i), again.get(i)), "Bonus type must follow the chest seed");
            }
        }
        h.assertTrue(total >= 100 && total <= 200, "5% bonus rate must be near 150/3000, got " + total);
        h.assertTrue(random >= total * .18 && random <= total * .42, "30% of tubes must be random, got " + random + "/" + total);
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void ownCratesNeverGainSignalBonus(GameTestHelper h) {
        var params = new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        for (var type : AirdropTypes.all().values()) for (int seed = 1; seed <= 100; seed++) {
            var loot = AirdropValidation.lootTable(h.getLevel().getServer(), type.lootTable()).getRandomItems(params, seed);
            h.assertTrue(loot.stream().noneMatch(s -> s.is(ModItems.SIGNAL_TUBE.get())), "Only vanilla structure chests receive the default bonus");
        }
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void distanceAndReloadDoNotResetRangeOrTriggerDelivery(GameTestHelper h) {
        var manager = AirdropEvents.get(h.getLevel().getServer());
        int before = manager.all().size();
        var flare = ModEntities.SIGNAL_FLARE.get().create(h.getLevel());
        Vec3 column = h.absoluteVec(new Vec3(2, 2, 2));
        Vec3 start = new Vec3(column.x, h.getLevel().getMaxBuildHeight() - 16, column.z);
        flare.setPos(start); flare.setDeltaMovement(0, 0, 1.5);
        for (int i = 0; i < 24; i++) flare.tick();
        var saved = flare.saveWithoutId(new CompoundTag());
        var restored = ModEntities.SIGNAL_FLARE.get().create(h.getLevel());
        restored.load(saved);
        for (int i = 0; i < 7; i++) restored.tick();
        h.assertTrue(!restored.isRemoved(), "Flare must survive until its 48-block range");
        restored.tick();
        h.assertTrue(restored.isRemoved() && Math.abs(start.distanceTo(restored.position()) - 48) < .001, "Reloaded flare must burst at the remaining range");
        h.assertTrue(manager.all().size() == before, "Projectile burst must never request a second event");
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void directHitBurnsFiveSecondsAndBurstDealsThree(GameTestHelper h) {
        var direct = h.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2, 2.5));
        var nearby = h.spawnWithNoFreeWill(EntityType.PIG, new Vec3(2.5, 2, 4.1));
        float first = direct.getHealth(), second = nearby.getHealth();
        var flare = ModEntities.SIGNAL_FLARE.get().create(h.getLevel());
        flare.setPos(h.absoluteVec(new Vec3(.5, 2.5, 2.5))); flare.setDeltaMovement(3, 0, 0);
        flare.tick();
        h.assertTrue(flare.isRemoved(), "Direct collision must burst immediately");
        h.assertTrue(direct.getRemainingFireTicks() == 100, "Only directly struck creature must burn for five seconds");
        h.assertTrue(first - direct.getHealth() == 3 && second - nearby.getHealth() == 3, "Burst must deal three raw points, without duplicate impact damage");
        h.assertTrue(!nearby.isOnFire(), "Nearby targets must not receive direct-hit ignition");
        flare.detonate();
        h.assertTrue(first - direct.getHealth() == 3, "A repeated burst call must not apply damage twice");
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void wallStopsFlareAndBurstDoesNotBreakBlocks(GameTestHelper h) {
        for (int z = 0; z <= 4; z++) for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(2, y, z), Blocks.STONE);
        var behind = h.spawnWithNoFreeWill(EntityType.PIG, new Vec3(3.2, 2, 2.5));
        float before = behind.getHealth();
        var flare = ModEntities.SIGNAL_FLARE.get().create(h.getLevel());
        flare.setPos(h.absoluteVec(new Vec3(.5, 2.5, 2.5))); flare.setDeltaMovement(3, 0, 0);
        flare.tick();
        h.assertTrue(flare.isRemoved() && flare.position().distanceTo(h.absoluteVec(new Vec3(.5, 2.5, 2.5))) < 3,
                "Wall must burst the projectile before max range");
        h.assertTrue(h.getBlockState(new BlockPos(2, 2, 2)).is(Blocks.STONE), "Signal burst must preserve blocks");
        h.assertTrue(behind.getHealth() == before && !behind.isOnFire(), "Solid wall must shield creatures from the burst");
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal", timeoutTicks = 100)
    public static void successfulShotConsumesOnceAndFlightGateKeepsNextTube(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var manager = AirdropEvents.get(server);
        for (var old : manager.all()) manager.cancel(server, old.id);
        int min = AirdropConfig.MIN_DROP_DISTANCE.get(), max = AirdropConfig.MAX_DROP_DISTANCE.get();
        var player = h.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setPos(h.absoluteVec(new Vec3(2.5, 2, 2.5)));
        h.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        var type = AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:food"));
        var stack = SignalTubeItem.boundStack(type); stack.setCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            // Synchronous isolated batch: make the one test column a valid landing point.
            AirdropConfig.MIN_DROP_DISTANCE.set(0); AirdropConfig.MAX_DROP_DISTANCE.set(0);
            var result = ModItems.SIGNAL_TUBE.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult().consumesAction() && stack.getCount() == 1, "Successful shot must consume exactly one tube");
            h.assertTrue(manager.all().size() == 1, "A shot must request exactly one delivery");
            var cargo = manager.all().iterator().next().cargo;
            h.assertTrue(cargo.toString().contains(type.name().getString()) || cargo.toString().contains("type.food"), "Bound tube must choose the food type");
            player.getCooldowns().removeCooldown(ModItems.SIGNAL_TUBE.get());
            var blocked = ModItems.SIGNAL_TUBE.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(blocked.getResult() == InteractionResult.FAIL && stack.getCount() == 1 && manager.all().size() == 1,
                    "Flight in progress must reject the second shot without spending a tube");
        } finally {
            AirdropConfig.MIN_DROP_DISTANCE.set(min); AirdropConfig.MAX_DROP_DISTANCE.set(max);
            for (var active : manager.all()) manager.cancel(server, active.id);
        }
        h.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty", batch = "signal")
    public static void missingBoundTypeRefusesWithoutSpending(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        var stack = SignalTubeItem.boundStack(ResourceLocation.parse("gone:removed"), Component.literal("Removed"));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = ModItems.SIGNAL_TUBE.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result.getResult() == InteractionResult.FAIL && stack.getCount() == 1, "Missing datapack type must not silently become a random event");
        h.succeed();
    }
}
