package com.prtsnote.airdrop;

import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("airdrop_supply_drops")
@PrefixGameTestTemplate(false)
public final class AirdropEventGameTests {
    @GameTest(template = "empty")
    public static void flightAnimationRemainsContinuous(GameTestHelper helper) {
        var clock = new com.prtsnote.airdrop.world.entity.FlightAnimationClock();
        clock.synchronize(137);
        helper.assertTrue(clock.sample(0) == 137 && clock.sample(1) == 137, "Joining mid-flight must start at the received age");
        double lastFrame = clock.sample(1);
        for (int tick = 1; tick <= 200; tick++) {
            // Normal three-tick samples, a delayed burst, then steady delivery again.
            if (tick % 3 == 0 && (tick < 60 || tick > 72)) {
                double before = clock.sample(0.6F);
                clock.synchronize(137 + tick);
                helper.assertTrue(clock.sample(0.6F) == before, "Receiving a packet must not jump the current frame");
            }
            double endOfPreviousTick = clock.sample(1);
            clock.tick();
            helper.assertTrue(Math.abs(clock.sample(0) - endOfPreviousTick) < 0.000001, "Tick rollover must remain continuous");
            double step = clock.sample(1) - clock.sample(0);
            helper.assertTrue(step >= 0.749999 && step <= 1.250001, "Correction must not teleport or reverse the plane");
            for (int frame = 0; frame <= 8; frame++) {
                double age = clock.sample(frame / 8F);
                helper.assertTrue(age >= lastFrame && age - lastFrame <= 0.157, "Frames must move smoothly forward between packets");
                lastFrame = age;
            }
        }
        helper.assertTrue(Math.abs(clock.sample(1) - 337) < 3, "Smoothed time must stay near the authoritative flight");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = AirdropEvents.RELEASE_TICK + 610)
    public static void fullEventAndRecovery(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 180 - helper.absolutePos(BlockPos.ZERO).getY(), 2);
        var level = helper.getLevel();
        var server = level.getServer();
        var absolute = helper.absolutePos(relative);
        level.setBlockAndUpdate(absolute.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(absolute, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(absolute.above(), Blocks.AIR.defaultBlockState());
        var manager = AirdropEvents.get(server);
        // A failed prior test run may have left an event in this dedicated test world.
        for (var previous : manager.all()) manager.cancel(server, previous.id);
        var type = AirdropTypes.all().get(new ResourceLocation("example_airdrops:medical"));
        var pos = helper.absolutePos(relative);
        // The default fixture is only five blocks tall; clear remnants of old test markers above it.
        for (int y = 0; y < 64; y++) level.setBlockAndUpdate(pos.above(y), Blocks.AIR.defaultBlockState());
        var id = manager.begin(level, pos, type);
        helper.assertTrue(id != null, "Event must start");
        helper.assertTrue(manager.begin(level, pos.east(), type) == null, "Active event limit must reject a second event");
        var initial = manager.find(id);
        var originalDropId = initial.dropId();
        var originalCargo = initial.cargo.copy();
        var plane = (com.prtsnote.airdrop.world.entity.AirdropPlane) level.getEntity(initial.planeId);
        var releasePoint = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 60, pos.getZ() + 0.5);
        helper.assertTrue(plane != null && Math.abs(plane.visualPosition().distanceTo(releasePoint) - 600) < 0.000001,
                "Spawned aircraft must appear 600 blocks before the landing point");
        helper.assertTrue(Math.abs(com.prtsnote.airdrop.world.entity.AirdropPlane.visualPosition(initial, AirdropEvents.DEPARTURE_TICK)
                .distanceTo(releasePoint) - 400) < 0.000001, "Departure must be 400 blocks beyond the landing point");
        helper.runAfterDelay(AirdropEvents.FLARE_FIRST_TICK - 1, () -> helper.assertTrue(AirdropEvents.get(server).find(id).flares == 0, "No flare before the approach sequence"));
        helper.runAfterDelay(AirdropEvents.FLARE_FIRST_TICK + 2, () -> helper.assertTrue(AirdropEvents.get(server).find(id).flares == 1, "First flare six seconds before release"));
        helper.runAfterDelay(AirdropEvents.FLARE_FIRST_TICK + AirdropEvents.FLARE_INTERVAL + 2, () -> helper.assertTrue(AirdropEvents.get(server).find(id).flares == 2, "Second flare four seconds before release"));
        helper.runAfterDelay(AirdropEvents.FLARE_FIRST_TICK + 50, () -> {
            var old = AirdropEvents.get(server);
            CompoundTag saved = old.save(new CompoundTag());
            var legacy = saved.copy();
            legacy.remove("flight_path_version");
            for (var value : legacy.getList("events", 10)) {
                var entry = (CompoundTag) value;
                entry.putLong("started", entry.getLong("started") + AirdropEvents.LEGACY_FLIGHT_AGE_OFFSET);
            }
            var migrated = AirdropEvents.load(legacy);
            helper.assertTrue(migrated.find(id).started == old.find(id).started && migrated.find(id).flares == 2,
                    "Legacy event ages must preserve the visible position and flare progress");
            var legacyPlane = plane.saveWithoutId(new CompoundTag());
            legacyPlane.remove("flight_path_version");
            legacyPlane.putInt("flight_age", plane.flightAge() - AirdropEvents.LEGACY_FLIGHT_AGE_OFFSET);
            var migratedPlane = com.prtsnote.airdrop.registry.ModEntities.PLANE.get().create(level);
            migratedPlane.load(legacyPlane);
            helper.assertTrue(migratedPlane.flightAge() == plane.flightAge()
                    && migratedPlane.visualPosition().distanceTo(plane.visualPosition()) < 0.000001,
                    "Legacy aircraft NBT must resume at the same rendered position");
            old.stopSession(server);
            var restored = AirdropEvents.load(saved);
            server.overworld().getDataStorage().set("airdrop_supply_drops_events", restored);
            restored.startSession(server);
            helper.assertTrue(restored.find(id).settings.lifetimeSeconds() == 600 && !restored.find(id).settings.resetOnRejoin(), "Restart must preserve type settings");
            helper.assertTrue(restored.find(id).flares == 2, "Reload must preserve flare progress");
            helper.assertTrue(restored.find(id).dropId().equals(originalDropId), "Reload must preserve cargo UUID");
        });
        helper.runAfterDelay(AirdropEvents.FLARE_FIRST_TICK + 2 * AirdropEvents.FLARE_INTERVAL + 2, () -> helper.assertTrue(AirdropEvents.get(server).find(id).flares == 3, "Third flare two seconds before release"));
        helper.runAfterDelay(AirdropEvents.RELEASE_TICK - 1, () -> helper.assertTrue(level.getEntity(originalDropId) == null, "No cargo before all flare rounds finish"));
        helper.runAfterDelay(AirdropEvents.RELEASE_TICK + 5, () -> {
            var event = AirdropEvents.get(server).find(id);
            helper.assertTrue(event.stage == AirdropEvents.Stage.FALLING, "Must transition to falling");
            helper.assertTrue(event.flares == 3 && level.getEntity(originalDropId) != null, "Exactly three rounds before release");
        });
        helper.runAfterDelay(AirdropEvents.RELEASE_TICK + 60, () -> {
            // Simulate an unloaded entity: recovery must reuse the same UUID and saved inventory.
            var drop = level.getEntity(originalDropId);
            helper.assertTrue(drop != null, "Drop must exist before recovery test");
            drop.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        });
        helper.runAfterDelay(AirdropEvents.RELEASE_TICK + 100, () -> {
            var event = AirdropEvents.get(server).find(id);
            helper.assertTrue(level.getEntity(originalDropId) != null, "Missing cargo must recover from saved snapshot; event="
                    + (event == null ? "missing" : event.stage + " at=" + event.ground + " expected=" + pos));
        });
        helper.runAfterDelay(AirdropEvents.DEPARTURE_TICK - 5, () -> {
            var departing = (com.prtsnote.airdrop.world.entity.AirdropPlane) level.getEntity(initial.planeId);
            helper.assertTrue(departing != null && Math.abs(departing.visualPosition().distanceTo(releasePoint) - 395) <= 2,
                    "Aircraft must continue nearly 400 blocks past the drop before disappearing");
        });
        helper.runAfterDelay(AirdropEvents.DEPARTURE_TICK + 5, () -> helper.assertTrue(level.getEntity(initial.planeId) == null, "Plane must leave after its flight"));
        helper.runAfterDelay(AirdropEvents.RELEASE_TICK + 560, () -> {
            var event = AirdropEvents.get(server).find(id);
            helper.assertTrue(event != null && event.stage == AirdropEvents.Stage.LANDED, "Event must persist through landing; event=" + event
                    + " block=" + level.getBlockState(pos) + " be=" + level.getBlockEntity(pos));
            var crate = (AirdropCrateBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(event.deadline - AirdropEvents.now(server) > 11000, "Landed event must use the captured ten-minute lifetime");
            helper.assertTrue(crate != null && id.equals(crate.eventId()), "Landed crate must belong to the event");
            var contents = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
            net.minecraft.world.ContainerHelper.loadAllItems(originalCargo, contents);
            for (int i = 0; i < 27; i++) helper.assertTrue(ItemStack.matches(contents.get(i), crate.getItem(i)), "Recovery changed inventory at slot " + i);
            helper.assertTrue(AirdropEvents.get(server).begin(level, pos.east(), type) == null, "Landed crate must retain active slot");
            AirdropEvents.get(server).cancel(server, id);
            helper.assertTrue(level.getBlockEntity(pos) == null && AirdropEvents.get(server).find(id) == null, "Cancel must clear crate and event");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Cancel must not drop loot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void weightedTypesAndSchedulePersistence(GameTestHelper helper) {
        int minerals = 0, food = 0, custom = 0, medical = 0;
        for (int i = 0; i < 12000; i++) {
            var type = AirdropEvents.chooseType(helper.getLevel());
            helper.assertTrue(type != null, "Type selection must succeed");
            switch (type.id().toString()) {
                case "airdrop_supply_drops:mineral" -> minerals++;
                case "airdrop_supply_drops:food" -> food++;
                case "example_airdrops:survival" -> custom++;
                case "example_airdrops:medical" -> medical++;
                default -> throw new AssertionError("Unexpected type");
            }
        }
        helper.assertTrue(minerals > 2000 && minerals < 2800 && food > 2000 && food < 2800 && custom > 4300 && custom < 5300 && medical > 2000 && medical < 2800,
                "Type weights must approximate 1:1:2:1");
        var scheduler = new AirdropEvents();
        long now = AirdropEvents.now(helper.getLevel().getServer());
        scheduler.schedule(helper.getLevel().getServer(), now);
        long next = scheduler.nextEventAt();
        helper.assertTrue(next >= now + 24000 && next <= now + 36000, "Default interval must be 20..30 minutes");
        var restored = AirdropEvents.load(scheduler.save(new CompoundTag()));
        helper.assertTrue(restored.nextEventAt() == next, "Restart must preserve next scheduled event");
        helper.succeed();
    }
}
