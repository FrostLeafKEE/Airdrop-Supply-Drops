package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AirdropMod.MOD_ID);
    public static final RegistryObject<SoundEvent> ENGINE = register("aircraft_engine");
    public static final RegistryObject<SoundEvent> FLARE = register("flare_launch");
    public static final RegistryObject<SoundEvent> PARACHUTE = register("parachute_open");
    public static final RegistryObject<SoundEvent> LANDING = register("crate_land");
    public static final RegistryObject<SoundEvent> WIND = register("descent_wind");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(AirdropMod.MOD_ID, name)));
    }
    private ModSounds() {}
}
