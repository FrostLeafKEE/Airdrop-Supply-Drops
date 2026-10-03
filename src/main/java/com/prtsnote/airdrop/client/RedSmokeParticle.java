package com.prtsnote.airdrop.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

final class RedSmokeParticle extends TextureSheetParticle {
    RedSmokeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 65 + random.nextInt(25);
        quadSize = 0.3F;
        xd = (random.nextDouble() - 0.5) * 0.025;
        yd = 0.055;
        zd = (random.nextDouble() - 0.5) * 0.025;
        hasPhysics = false;
        alpha = 0;
    }
    @Override public void tick() {
        super.tick();
        float progress = age / (float) lifetime;
        quadSize = 0.3F + progress * 1.4F;
        alpha = 0.7F * Math.min(1, age / 8F) * (1 - progress);
        yd = 0.055;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
