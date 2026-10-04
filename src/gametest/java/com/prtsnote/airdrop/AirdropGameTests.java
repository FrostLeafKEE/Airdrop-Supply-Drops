package com.prtsnote.airdrop;

import com.google.gson.JsonParser;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("airdrop_supply_drops")
@PrefixGameTestTemplate(false)
public final class AirdropGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static AirdropCrateBlockEntity crate(GameTestHelper helper) {
        helper.setBlock(POS.below(), Blocks.STONE);
        var type = AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:mineral"));
        helper.assertTrue(type != null, "Built-in mineral type must load");
        helper.assertTrue(AirdropServer.placeCrate(helper.getLevel(), helper.absolutePos(POS), type), "Crate must place");
        return (AirdropCrateBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }

    @GameTest(template = "empty")
    public static void hopperCannotExtractCrateContents(GameTestHelper helper) {
        var crate = crate(helper);
        int before = 0;
        for (int slot = 0; slot < crate.getContainerSize(); slot++) before += crate.getItem(slot).getCount();
        helper.setBlock(POS.below(), Blocks.HOPPER);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS.below()));
        for (int attempt = 0; attempt < 20; attempt++) {
            helper.assertTrue(!net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(helper.getLevel(), hopper),
                    "Hopper must not extract from a supply crate");
        }
        int after = 0;
        for (int slot = 0; slot < crate.getContainerSize(); slot++) after += crate.getItem(slot).getCount();
        helper.assertTrue(before == after && hopper.isEmpty(), "Blocked extraction must preserve both inventories");
        for (var side : net.minecraft.core.Direction.values()) {
            helper.assertTrue(crate.getSlotsForFace(side).length == 0, "No face may expose automation slots");
        }
        for (int slot = 0; slot < crate.getContainerSize(); slot++) if (!crate.getItem(slot).isEmpty()) {
            helper.assertTrue(!crate.removeItem(slot, 1).isEmpty(), "Player menu extraction must remain possible");
            break;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void descendingCrateVisibleAtEventDistance(GameTestHelper helper) {
        var drop = com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get().create(helper.getLevel());
        helper.assertTrue(drop.shouldRenderAtSqrDistance(200 * 200), "Crate and parachute must render at the maximum landing distance");
        helper.assertTrue(drop.shouldRenderAtSqrDistance(200 * 200 + 60 * 60), "Flight height must fit inside the render range");
        helper.assertTrue(drop.shouldRenderAtSqrDistance(255 * 255), "Render range must cover the smoke range");
        helper.assertTrue(!drop.shouldRenderAtSqrDistance(300 * 300), "Render distance must remain bounded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodCrateContainsVanillaFood(GameTestHelper helper) {
        var type = AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:food"));
        helper.assertTrue(type != null && type.weight() > 0, "Food airdrop must load and participate in automatic selection");
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.assertTrue(AirdropServer.placeCrate(helper.getLevel(), helper.absolutePos(POS), type), "Food crate must place");
        var crate = (AirdropCrateBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        helper.assertTrue(crate.getBlockState().getValue(com.prtsnote.airdrop.world.block.AirdropCrateBlock.FOOD), "Food appearance must be applied");
        helper.assertTrue(!crate.isEmpty() && crate.getDisplayName().equals(type.name()), "Food crate must have loot and its own title");
        var table = helper.getLevel().getServer().getLootData().getLootTable(type.lootTable());
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        var seen = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (int seed = 1; seed <= 1000; seed++) {
            var loot = table.getRandomItems(params, seed);
            helper.assertTrue(!loot.isEmpty(), "Food loot must not be empty");
            for (var stack : loot) {
                helper.assertTrue(stack.isEdible() && net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals("minecraft"),
                        "Food airdrop must contain only vanilla edible items");
                seen.add(stack.getItem());
            }
        }
        helper.assertTrue(seen.containsAll(java.util.List.of(Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_CHICKEN,
                Items.COOKED_MUTTON, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BREAD)),
                "Food loot must include cooked meats, cooked fish and bread");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exampleDatapackLoadsAndFills(GameTestHelper helper) {
        var type = AirdropTypes.all().get(new ResourceLocation("example_airdrops:survival"));
        helper.assertTrue(type != null, "Example datapack type must load from world/datapacks");
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.assertTrue(AirdropServer.placeCrate(helper.getLevel(), helper.absolutePos(POS), type), "Custom crate must place");
        var crate = (AirdropCrateBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
        helper.assertTrue(!crate.isEmpty(), "Custom loot table must fill crate");
        helper.assertTrue(crate.getDisplayName().equals(type.name()), "Custom title must be preserved");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyDeadlineIsIgnoredAfterReload(GameTestHelper helper) {
        var crate = crate(helper);
        var registries = helper.getLevel().registryAccess();
        var saved = crate.saveWithFullMetadata();
        saved.remove("smoke_ends_at");
        saved.putInt("remaining_ticks", 0);
        saved.putBoolean("timer_started", true);
        saved.putLong("expires_at", 0);
        saved.putUUID("event_id", java.util.UUID.randomUUID());
        var inventory = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(saved, inventory);
        helper.runAfterDelay(4, () -> {
            crate.load(saved);
            crate.onLoad();
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) == crate, "Expired/orphaned legacy crates must survive reload");
            for (int slot = 0; slot < 27; slot++) helper.assertTrue(ItemStack.matches(inventory.get(slot), crate.getItem(slot)), "Legacy expiry must preserve inventory");
            helper.assertTrue(!crate.saveWithFullMetadata().contains("expires_at"), "Resaving must discard the old expiration field");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void retiredEventDoesNotPreventClaimingContents(GameTestHelper helper) {
        var crate = crate(helper);
        crate.clearContent();
        crate.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        crate.bindEvent(java.util.UUID.randomUUID());
        crate.dropContents(helper.getLevel(), helper.absolutePos(POS));
        helper.assertTrue(itemsNear(helper).stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 7,
                "An orphaned or retired event must not make crate contents unavailable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void descentTransfersSnapshot(GameTestHelper helper) {
        helper.setBlock(POS.below(), Blocks.STONE);
        var drop = com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get().create(helper.getLevel());
        BlockPos start = helper.absolutePos(POS.above(2));
        drop.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        helper.assertTrue(drop.prepare(AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:mineral"))), "Drop must prepare");
        var saved = new net.minecraft.nbt.CompoundTag();
        drop.saveWithoutId(saved);
        var contents = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(saved, contents);
        helper.getLevel().addFreshEntity(drop);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(drop.isRemoved(), "Falling entity must disappear on landing");
            var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(POS));
            helper.assertTrue(blockEntity instanceof AirdropCrateBlockEntity, "Landing must create a crate");
            var crate = (AirdropCrateBlockEntity) blockEntity;
            for (int i = 0; i < 27; i++) {
                helper.assertTrue(ItemStack.matches(contents.get(i), crate.getItem(i)), "Landing must preserve slot " + i);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void appearanceAndDeploymentSurviveReload(GameTestHelper helper) {
        var type = AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:food"));
        var level = helper.getLevel();
        var drop = com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get().create(level);
        var pos = helper.absolutePos(POS);
        drop.setPos(pos.getX() + 0.5, 230, pos.getZ() + 0.5);
        helper.assertTrue(drop.prepare(type) && drop.isFood(), "Food appearance must be selected");
        level.addFreshEntity(drop);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(drop.deploymentTicks() > 10 && drop.deploymentTicks() < 30, "Canopy must be opening");
            var saved = drop.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var copy = com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get().create(level);
            copy.load(saved);
            helper.assertTrue(copy.isFood() && copy.deploymentTicks() == drop.deploymentTicks(), "Reload must preserve appearance and canopy progress");
        });
        helper.runAfterDelay(32, () -> {
            helper.assertTrue(drop.deploymentTicks() == 30 && Math.abs(drop.getDeltaMovement().y + 0.12) < 0.00001,
                    "Fully open canopy must use slow descent speed");
            drop.discard();
            helper.setBlock(POS.below(), Blocks.STONE);
            helper.assertTrue(AirdropServer.placeCrate(level, pos, type), "Food crate must place");
            helper.assertTrue(level.getBlockState(pos).getValue(com.prtsnote.airdrop.world.block.AirdropCrateBlock.FOOD), "Landed crate must use food model");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void inventoryRoundTrip(GameTestHelper helper) {
        var crate = crate(helper);
        helper.assertTrue(!crate.isEmpty(), "Loot table must fill crate");
        crate.clearContent();
        crate.setItem(0, new ItemStack(Items.DIAMOND, 1));
        var saved = crate.saveWithFullMetadata();
        crate.setItem(1, new ItemStack(Items.DIRT));
        crate.load(saved);
        helper.assertTrue(crate.getItem(0).is(Items.DIAMOND) && crate.getItem(0).getCount() == 1, "Saved diamond must survive");
        helper.assertTrue(crate.getItem(1).isEmpty(), "Loading must remove stale slots");
        helper.assertTrue(!crate.canPlaceItem(0, new ItemStack(Items.DIRT)), "Container must reject insertion");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fiveMinuteSmokeDeadlineKeepsLoot(GameTestHelper helper) {
        var crate = crate(helper);
        var registries = helper.getLevel().registryAccess();
        var saved = crate.saveWithFullMetadata();
        saved.putLong("smoke_ends_at", com.prtsnote.airdrop.server.AirdropEvents.now(helper.getLevel().getServer()) + 2);
        crate.load(saved);
        helper.assertTrue(crate.emitsSmoke(), "Smoke must still be enabled just before five minutes");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) == crate && !crate.isEmpty(), "The smoke deadline must preserve the crate and supplies");
            helper.assertTrue(!crate.emitsSmoke(), "Smoke must stop after five minutes");
            var again = crate.saveWithFullMetadata();
            crate.load(again); crate.onLoad();
            helper.assertTrue(!crate.emitsSmoke(), "Reload must not restart expired smoke");
            helper.assertTrue(itemsNear(helper).isEmpty(), "Smoke stopping must not scatter loot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void emptyCrateRemains(GameTestHelper helper) {
        var crate = crate(helper);
        crate.clearContent();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) == crate && !crate.emitsSmoke(), "An empty crate must remain without smoke");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void playerBreakScattersContentsAndPlanks(GameTestHelper helper) {
        var crate = crate(helper);
        crate.clearContent();
        crate.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
        var pos = helper.absolutePos(POS);
        var state = helper.getLevel().getBlockState(pos);
        state.getBlock().playerWillDestroy(helper.getLevel(), pos, state, helper.makeMockPlayer());
        helper.getLevel().destroyBlock(pos, true);
        int iron = itemsNear(helper).stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum();
        int planks = itemsNear(helper).stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.OAK_PLANKS)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(iron == 7 && planks == 4 && itemsNear(helper).stream().allMatch(e -> e.getItem().is(Items.IRON_INGOT) || e.getItem().is(Items.OAK_PLANKS)),
                "Breaking must scatter remaining supplies and exactly four planks without a crate item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningToolsNeverRecoverCrate(GameTestHelper helper) {
        var crate = crate(helper);
        var pos = helper.absolutePos(POS);
        var state = crate.getBlockState();
        var silk = new ItemStack(Items.DIAMOND_AXE);
        var fortune = new ItemStack(Items.DIAMOND_AXE);
        silk.enchant(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, 1);
        fortune.enchant(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, 3);
        for (var tool : java.util.List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_AXE), silk, fortune)) {
            var drops = net.minecraft.world.level.block.Block.getDrops(state,helper.getLevel(),pos,crate,null,tool);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.OAK_PLANKS) && drops.get(0).getCount() == 4,
                    "Hand, axe, Silk Touch and Fortune must yield exactly four planks");
        }
        helper.assertTrue(state.getBlock().getCloneItemStack(helper.getLevel(),pos,state).isEmpty(), "Picking the crate must not create a crate item");
        helper.succeed();
    }

    private static java.util.List<ItemEntity> itemsNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(1));
    }

    @GameTest(template = "empty")
    public static void invalidTypesAreRejected(GameTestHelper helper) {
        for (String json : new String[]{
                "{\"schema_version\":2}",
                "{\"schema_version\":1.5}",
                "{\"schema_version\":1,\"weight\":0.5}",
                "{\"schema_version\":1,\"weight\":0}",
                "{\"schema_version\":1,\"weight\":1,\"appearance\":\"invalid\"}"}) {
            boolean rejected = false;
            try { AirdropTypes.parse(new ResourceLocation("test:invalid"), JsonParser.parseString(json).getAsJsonObject()); }
            catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid type must be rejected: " + json);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mineralRarityAndCounts(GameTestHelper helper) {
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        var table = helper.getLevel().getServer().getLootData().getLootTable(new ResourceLocation("airdrop_supply_drops:airdrop/mineral"));
        int rolls = 0;
        int diamonds = 0;
        int ironNuggets = 0;
        for (int i = 1; i <= 10000; i++) {
            var loot = table.getRandomItems(params, i);
            helper.assertTrue(loot.size() >= 8 && loot.size() <= 14, "Each crate must roll 8..14 times");
            rolls += loot.size();
            for (ItemStack stack : loot) {
                helper.assertTrue(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace().equals("minecraft"),
                        "Default mineral loot must contain only vanilla items");
                if (stack.is(Items.DIAMOND)) {
                    helper.assertTrue(stack.getCount() == 1, "Legendary roll must yield exactly one diamond");
                    diamonds++;
                }
                if (stack.is(Items.IRON_NUGGET)) {
                    helper.assertTrue(stack.getCount() >= 8 && stack.getCount() <= 16, "Common count must be 8..16");
                    ironNuggets++;
                }
            }
        }
        double ratio = (double) diamonds / rolls;
        helper.assertTrue(ratio > 0.008 && ratio < 0.012, "Diamond frequency must approximate 1%, actual=" + ratio);
        helper.assertTrue(ironNuggets > 0, "Common table must contain iron nuggets");
        helper.succeed();
    }
}
