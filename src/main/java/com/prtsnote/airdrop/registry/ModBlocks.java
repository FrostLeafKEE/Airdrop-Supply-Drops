package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.block.AirdropCrateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AirdropMod.MOD_ID);

    public static final RegistryObject<Block> AIRDROP_CRATE = BLOCKS.register(
            "airdrop_crate",
            () -> new AirdropCrateBlock(Block.Properties.copy(Blocks.CHEST).strength(0.65F, 3600000.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion()));

    private ModBlocks() {
    }
}
