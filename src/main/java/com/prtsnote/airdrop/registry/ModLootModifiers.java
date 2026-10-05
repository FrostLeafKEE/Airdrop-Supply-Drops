package com.prtsnote.airdrop.registry;

import com.mojang.serialization.Codec;
import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.loot.SignalTubeLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AirdropMod.MOD_ID);
    static { MODIFIERS.register("signal_tube", () -> SignalTubeLootModifier.CODEC); }
    private ModLootModifiers() {}
}
