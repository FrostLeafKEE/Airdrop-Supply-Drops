package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AirdropMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<AirdropCrateBlockEntity>> AIRDROP_CRATE =
            BLOCK_ENTITIES.register(
                    "airdrop_crate",
                    () -> BlockEntityType.Builder.of(AirdropCrateBlockEntity::new, ModBlocks.AIRDROP_CRATE.get()).build(null));

    private ModBlockEntities() {
    }
}
