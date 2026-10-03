package com.prtsnote.airdrop.world.entity;

/** Client presentation time: packet updates correct speed, never teleport the rendered frame. */
public final class FlightAnimationClock {
    private boolean initialized;
    private double previous, current, target;

    public void synchronize(int serverAge) {
        target = serverAge;
        if (!initialized) {
            previous = current = target;
            initialized = true;
        }
    }

    public void tick() {
        if (!initialized) return;
        previous = current;
        target++;
        double correction = Math.max(-0.25, Math.min(0.25, (target - current - 1) * 0.1));
        current += 1 + correction;
    }

    public double sample(float partialTick) {
        return previous + (current - previous) * Math.max(0, Math.min(1, partialTick));
    }
}
