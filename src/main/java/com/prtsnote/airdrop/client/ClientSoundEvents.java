package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AirdropMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSoundEvents {
    private static final Map<BlockPos, SmokeHissSound> SMOKE_LOOPS = new HashMap<>();
    private ClientSoundEvents() {}

    @SubscribeEvent public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AirdropPlane plane) {
            Minecraft.getInstance().getSoundManager().play(new AircraftEngineSound(plane));
        }
        if (event.getLevel().isClientSide() && event.getEntity() instanceof com.prtsnote.airdrop.world.entity.FallingAirdrop drop) {
            Minecraft.getInstance().getSoundManager().play(new DescentWindSound(drop));
        }
    }

    public static void tickCrate(AirdropCrateBlockEntity crate) {
        var pos = crate.getBlockPos();
        var previous = SMOKE_LOOPS.get(pos);
        if (previous != null) {
            if (previous.belongsTo(crate) && !previous.isStopped() && previous.shouldContinue()) return;
            previous.cancel();
            SMOKE_LOOPS.remove(pos);
        }
        var client = Minecraft.getInstance();
        var camera = client.getCameraEntity();
        if (!crate.isClientSmoking() || camera == null || camera.distanceToSqr(
                pos.getX() + .5, pos.getY() + 1.1, pos.getZ() + .5) > 32 * 32) return;
        var sound = new SmokeHissSound(crate);
        SMOKE_LOOPS.put(pos.immutable(), sound);
        client.getSoundManager().play(sound);
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // Unloaded chunks no longer tick their crates; retire their loops here too.
        SMOKE_LOOPS.values().removeIf(sound -> {
            if (!sound.isStopped() && sound.shouldContinue()) return false;
            sound.cancel();
            return true;
        });
    }

    @Mod.EventBusSubscriber(modid = AirdropMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
        @SubscribeEvent public static void soundEngineLoaded(net.minecraftforge.client.event.sound.SoundEngineLoadEvent event) {
            Minecraft.getInstance().execute(() -> {
                SMOKE_LOOPS.values().forEach(SmokeHissSound::cancel);
                SMOKE_LOOPS.clear();
            });
        }
    }
}
