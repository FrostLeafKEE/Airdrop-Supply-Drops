package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AirdropMod.MOD_ID);
    public static final RegistryObject<SimpleParticleType> RED_SMOKE = PARTICLES.register("red_smoke", () -> new SimpleParticleType(true));
    private ModParticles() {}
}
