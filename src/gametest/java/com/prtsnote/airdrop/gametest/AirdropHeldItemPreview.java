package com.prtsnote.airdrop.gametest;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.world.item.SignalTubeItem;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional native held-item screenshots in a copied, isolated development world. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropHeldItemPreview {
    private static boolean opening;
    private static volatile boolean prepared, done;
    private static int ticks, stage = -1, frames;
    private static volatile int requestedStage = -1;

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("airdrop.heldPreview") || done || event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().get(0);
        if (!prepared) {
            for (var active : com.prtsnote.airdrop.server.AirdropEvents.get(server).all()) {
                com.prtsnote.airdrop.server.AirdropEvents.get(server).cancel(server, active.id);
            }
            level.setDayTime(6000); level.setWeatherParameters(6000, 0, false, false);
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 179, z), Blocks.STONE.defaultBlockState());
                for (int y = 180; y <= 184; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, SignalTubeItem.randomStack());
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.teleportTo(level, .5, 180, .5, 0, 0);
            prepared = true;
        }
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("airdrop.heldPreview") || done || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        try {
            if (++ticks > 1600) throw new IllegalStateException("Held-item preview timed out");
            if (!opening && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                opening = true;
                client.createWorldOpenFlows().openWorld("airdrop-heldpreview", () -> client.setScreen(new TitleScreen()));
            }
            if (client.level == null || client.player == null || !prepared || client.getOverlay() != null || client.screen != null) return;
            if (stage < 0) stage = 0;
            if (requestedStage != stage) {
                client.player.setMainArm(stage >= 2 ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                client.options.mainHand().set(stage >= 2 ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                client.options.setCameraType(stage % 2 == 0 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT);
                client.player.setYRot(0); client.player.setXRot(0);
                client.player.yBodyRot = 0; client.player.yHeadRot = 0;
                client.options.hideGui = false;
                client.options.fov().set(70);
                frames = 0; requestedStage = stage;
            }
            if (++frames < 25) return;
            String name = "signal-held-" + (stage % 2 == 0 ? "first" : "third") + "-" + (stage < 2 ? "right" : "left") + ".png";
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve(name));
            }
            LogUtils.getLogger().info("AIRDROP_HELD_PREVIEW: {}", name);
            if (++stage == 4) {
                LogUtils.getLogger().info("AIRDROP_HELD_PREVIEW_COMPLETE: native first/third-person, left/right-hand screenshots");
                done = true; client.stop();
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_HELD_PREVIEW_FAILED", error);
            done = true; client.stop();
        }
    }
}
