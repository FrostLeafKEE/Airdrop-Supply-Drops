package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AirdropMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.prtsnote.airdrop.registry.ModParticles.RED_SMOKE.get(), sprites ->
                (options, level, x, y, z, dx, dy, dz) -> new RedSmokeParticle(level, x, y, z, sprites, options));
    }

    @SubscribeEvent
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get(), FallingAirdropRenderer::new);
        event.registerEntityRenderer(com.prtsnote.airdrop.registry.ModEntities.PLANE.get(), AirdropPlaneRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.AIRDROP_CRATE.get(), AirdropCrateScreen::new));
    }
}
