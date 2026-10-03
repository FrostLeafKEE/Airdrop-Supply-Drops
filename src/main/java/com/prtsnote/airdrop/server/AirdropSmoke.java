package com.prtsnote.airdrop.server;

import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.particle.SmokeParticleOptions;
import net.minecraft.server.level.ServerLevel;

public final class AirdropSmoke {
    private AirdropSmoke() {}

    public static void emit(ServerLevel level, double x, double y, double z, boolean airborne) {
        if (airborne && !AirdropConfig.AIRBORNE_SMOKE_ENABLED.get()) return;
        var smoke = new SmokeParticleOptions(AirdropConfig.smokeColor());
        for (var player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= 256 * 256) {
                level.sendParticles(player, smoke, true, x, y, z, 6, 0.18, airborne ? 0.3 : 0.1, 0.18, airborne ? 0.02 : 0);
            }
        }
    }
}
