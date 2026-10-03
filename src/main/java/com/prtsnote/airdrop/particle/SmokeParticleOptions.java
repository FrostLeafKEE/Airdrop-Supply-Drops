package com.prtsnote.airdrop.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.prtsnote.airdrop.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** RGB color carried in the server's particle packet, independent of client configuration. */
public record SmokeParticleOptions(int color) implements ParticleOptions {
    public static final MapCodec<SmokeParticleOptions> CODEC = Codec.intRange(0, 0xFFFFFF)
            .fieldOf("color").xmap(SmokeParticleOptions::new, SmokeParticleOptions::color);
    public static final StreamCodec<RegistryFriendlyByteBuf, SmokeParticleOptions> STREAM_CODEC = StreamCodec.of(
            (buffer, options) -> buffer.writeInt(options.color()),
            buffer -> new SmokeParticleOptions(buffer.readInt()));

    public SmokeParticleOptions {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Smoke color must be a 24-bit RGB value");
    }

    public float red() { return ((color >> 16) & 255) / 255F; }
    public float green() { return ((color >> 8) & 255) / 255F; }
    public float blue() { return (color & 255) / 255F; }

    @Override public ParticleType<SmokeParticleOptions> getType() { return ModParticles.RED_SMOKE.get(); }
}
