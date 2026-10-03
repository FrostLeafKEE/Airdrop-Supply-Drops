package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AirdropMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSoundEvents {
    private ClientSoundEvents() {}

    @SubscribeEvent public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AirdropPlane plane) {
            Minecraft.getInstance().getSoundManager().play(new AircraftEngineSound(plane));
        }
        if (event.getLevel().isClientSide() && event.getEntity() instanceof com.prtsnote.airdrop.world.entity.FallingAirdrop drop) {
            Minecraft.getInstance().getSoundManager().play(new DescentWindSound(drop));
        }
    }
}
