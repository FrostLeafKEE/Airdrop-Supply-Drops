package com.prtsnote.airdrop;

import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.registry.ModBlockEntities;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.registry.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AirdropMod.MOD_ID)
public final class AirdropMod {
    public static final String MOD_ID = "airdrop_supply_drops";

    public AirdropMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        com.prtsnote.airdrop.registry.ModItems.ITEMS.register(modEventBus);
        com.prtsnote.airdrop.registry.ModLootModifiers.MODIFIERS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        com.prtsnote.airdrop.registry.ModEntities.ENTITIES.register(modEventBus);
        com.prtsnote.airdrop.registry.ModSounds.SOUNDS.register(modEventBus);
        com.prtsnote.airdrop.registry.ModParticles.PARTICLES.register(modEventBus);
        modEventBus.addListener(AirdropConfig::onConfigLoading);
        modEventBus.addListener(AirdropConfig::onConfigReloading);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AirdropConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropServer::onReload);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropServer::onStarted);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropServer::onStopped);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropServer::onCommands);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropServer::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(com.prtsnote.airdrop.server.AirdropEvents::onTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent event) ->
                com.prtsnote.airdrop.server.AirdropEvents.get(event.getServer()).stopSession(event.getServer()));
    }

}
