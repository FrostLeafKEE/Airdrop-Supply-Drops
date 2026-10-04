package com.prtsnote.airdrop.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

final class RedSmokeParticle extends TextureSheetParticle {
    RedSmokeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                     com.prtsnote.airdrop.particle.SmokeParticleOptions options) {
        super(level, x, y, z);
        pickSprite(sprites);
        setColor(options.red(), options.green(), options.blue());
        lifetime = 160 + random.nextInt(20);
        quadSize = 0.22F;
        xd = (random.nextDouble() - 0.5) * 0.004;
        yd = 0.14;
        zd = (random.nextDouble() - 0.5) * 0.004;
        hasPhysics = false;
        alpha = 0;
    }
    @Override public void tick() {
        super.tick();
        float progress = age / (float) lifetime;
        // Keep a narrow column and fade only near the top instead of widening into a cloud.
        quadSize = 0.22F + progress * 0.28F;
        alpha = 0.7F * Math.min(1, age / 6F) * Math.min(1, (1 - progress) / 0.25F);
        yd = 0.14;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
