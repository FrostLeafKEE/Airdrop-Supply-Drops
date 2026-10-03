package com.prtsnote.airdrop.world.entity;

import net.minecraft.world.phys.Vec3;

/** Fill the ticks between authoritative position packets without moving on packet receipt. */
public final class DescentInterpolation {
    private Vec3 target;
    private int remainingSteps;

    public void synchronize(Vec3 position, int steps) {
        target = position;
        remainingSteps = Math.max(1, steps);
    }

    public Vec3 tick(Vec3 position) {
        if (remainingSteps == 0) return position;
        return position.lerp(target, 1.0 / remainingSteps--);
    }

    public Vec3 target(Vec3 position) {
        return remainingSteps > 0 ? target : position;
    }
}
