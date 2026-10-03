package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.particle.SmokeParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AirdropMod.MOD_ID);
    // Retain the resource ID so existing particle-description overrides still match.
    public static final RegistryObject<ParticleType<SmokeParticleOptions>> RED_SMOKE = PARTICLES.register("red_smoke",
            () -> new ParticleType<SmokeParticleOptions>(true) {
                @Override public com.mojang.serialization.MapCodec<SmokeParticleOptions> codec() { return SmokeParticleOptions.CODEC; }
                @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SmokeParticleOptions> streamCodec() {
                    return SmokeParticleOptions.STREAM_CODEC;
                }
            });
    private ModParticles() {}
}
