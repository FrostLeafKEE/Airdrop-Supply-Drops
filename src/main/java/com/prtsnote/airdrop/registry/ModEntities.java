package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.entity.FallingAirdrop;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AirdropMod.MOD_ID);
    public static final RegistryObject<EntityType<FallingAirdrop>> FALLING_AIRDROP = ENTITIES.register("falling_airdrop",
            () -> EntityType.Builder.<FallingAirdrop>of(FallingAirdrop::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(32).updateInterval(3).fireImmune()
                    .build("airdrop_supply_drops:falling_airdrop"));
    private ModEntities() {}
    public static final RegistryObject<EntityType<com.prtsnote.airdrop.world.entity.AirdropPlane>> PLANE = ENTITIES.register("airdrop_plane",
            () -> EntityType.Builder.<com.prtsnote.airdrop.world.entity.AirdropPlane>of(com.prtsnote.airdrop.world.entity.AirdropPlane::new, MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(32).updateInterval(3).fireImmune().build("airdrop_supply_drops:airdrop_plane"));
}
