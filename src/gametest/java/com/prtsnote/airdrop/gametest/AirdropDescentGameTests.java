package com.prtsnote.airdrop.gametest;

import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.world.entity.DescentInterpolation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropDescentGameTests {
    @GameTest(template = "airdrop_supply_drops:empty")
    public static void gravityAndCanopyLimitDescentSpeed(GameTestHelper helper) {
        var level = helper.getLevel();
        var drop = ModEntities.FALLING_AIRDROP.get().create(level);
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        drop.setPos(pos.getX() + 0.5, 260, pos.getZ() + 0.5);
        helper.assertTrue(drop.prepare(AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:food"))), "Cargo must prepare");
        double previousSpeed = 0;
        for (int tick = 1; tick <= 70; tick++) {
            double previousY = drop.getY();
            drop.tick();
            double speed = previousY - drop.getY();
            helper.assertTrue(!drop.isRemoved() && speed > 0 && speed <= 0.280001, "Gravity must cause bounded downward movement every tick");
            if (tick <= 7) helper.assertTrue(speed > previousSpeed, "The release must accelerate under gravity");
            if (tick == 10) {
                // Exercise a velocity saved by an old/externally modified entity: canopy drag must cap it.
                drop.setDeltaMovement(0, -3, 0);
            }
            if (tick >= 30) helper.assertTrue(Math.abs(speed - 0.12) < 0.000001, "An open canopy must descend at exactly 2.4 blocks per second");
            previousSpeed = speed;
        }
        var saved = drop.saveWithoutId(new CompoundTag());
        saved.putBoolean("NoGravity", true);
        var restored = ModEntities.FALLING_AIRDROP.get().create(level);
        restored.load(saved);
        helper.assertTrue(!restored.isNoGravity(), "Old no-gravity saves must migrate to gravity-enabled descent");
        double before = restored.getY();
        restored.tick();
        helper.assertTrue(Math.abs(before - restored.getY() - 0.12) < 0.000001 && restored.deploymentTicks() == 30,
                "Resuming an open canopy must preserve its fixed speed and deployment");
        helper.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void positionPacketsProduceContinuousDescent(GameTestHelper helper) {
        var interpolation = new DescentInterpolation();
        var position = new Vec3(4, 230, 8);
        helper.assertTrue(interpolation.target(position).equals(position), "Spawn position must be retained before the first movement packet");
        double lastFrame = position.y;
        for (int tick = 0; tick < 90; tick++) {
            if (tick % 3 == 0) {
                var target = new Vec3(4, 230 - (tick + 3) * 0.12, 8);
                interpolation.synchronize(target, 3);
                helper.assertTrue(position.y == lastFrame, "Receiving a packet must not jump the displayed position");
                helper.assertTrue(interpolation.target(position).equals(target), "Relative position updates must retain the authoritative target");
            }
            Vec3 next = interpolation.tick(position);
            helper.assertTrue(Math.abs(position.y - next.y - 0.12) < 0.000001, "Every client tick must move even between three-tick packets");
            for (int frame = 0; frame <= 8; frame++) {
                double renderedY = position.lerp(next, frame / 8.0).y;
                helper.assertTrue(renderedY <= lastFrame + 0.000001 && lastFrame - renderedY <= 0.015001,
                        "Partial-tick frames must descend continuously without a packet-sized jump");
                lastFrame = renderedY;
            }
            position = next;
        }
        helper.assertTrue(Math.abs(position.y - 219.2) < 0.000001, "Smoothing must converge to the authoritative position");
        helper.assertTrue(interpolation.tick(position).equals(position), "Client interpolation must stop at the last server target");
        helper.succeed();
    }
}
