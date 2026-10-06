package com.prtsnote.airdrop;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropRules;
import com.prtsnote.airdrop.data.AirdropTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in regression through the actual new-world screen, before any /reload. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropDimensionStartupCheck {
    private static boolean opening, creating, checked, finished;
    private static int ticks;
    private static volatile String failure;
    private static volatile boolean passed;

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("airdrop.dimensionCheck") || checked || event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        checked = true;
        try {
            var tag = "#tacz_airdrop:allowed_dimensions";
            var type = new ResourceLocation("airdrop_dimension_probe:probe");
            LogUtils.getLogger().info("AIRDROP_DIMENSION_FRESH_START: matches={}, type={}, diagnostics={}",
                    AirdropRules.matchesDimension(server.overworld(), tag), AirdropTypes.all().containsKey(type), AirdropTypes.diagnostics());
            require(AirdropRules.matchesDimension(server.overworld(), tag), "Fresh-world startup must resolve the partner's dimension tag without /reload");
            require(!AirdropRules.matchesDimension(server.getLevel(Level.NETHER), tag), "The group must not include the Nether");
            require(AirdropTypes.all().containsKey(type) && AirdropTypes.diagnostics().isEmpty(), "Startup must keep the tagged type valid");
            var previous = AirdropConfig.ALLOWED_DIMENSIONS.get();
            try {
                AirdropConfig.ALLOWED_DIMENSIONS.set(java.util.List.of(tag));
                require(AirdropConfig.isDimensionAllowed(server.overworld()) && !AirdropConfig.isDimensionAllowed(server.getLevel(Level.NETHER)),
                        "The server whitelist must resolve the same ID group");
            } finally { AirdropConfig.ALLOWED_DIMENSIONS.set(previous); }
            server.reloadResources(java.util.List.copyOf(server.getPackRepository().getSelectedIds())).thenRunAsync(() -> {
                try {
                    require(AirdropRules.matchesDimension(server.overworld(), tag) && AirdropTypes.all().containsKey(type)
                            && AirdropTypes.diagnostics().isEmpty(), "The tag must remain valid after reload");
                    passed = true;
                } catch (Exception error) { failure = error.toString(); }
            }, server).exceptionally(error -> { failure = error.toString(); return null; });
        } catch (Exception error) { failure = error.toString(); }
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("airdrop.dimensionCheck") || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        try {
            if (++ticks > 3600) throw new IllegalStateException("New-world dimension check timed out: " + client.screen);
            if (failure != null) throw new IllegalStateException(failure);
            if (!opening && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                opening = true;
                CreateWorldScreen.openFresh(client, client.screen);
            }
            if (!creating && client.screen instanceof CreateWorldScreen screen) {
                screen.getUiState().setName(System.getProperty("airdrop.dimensionCheckWorld"));
                screen.getUiState().setGenerateStructures(false);
                screen.getUiState().setSeed("20261006");
                var label = Component.translatable("selectWorld.create").getString();
                var button = screen.children().stream().filter(child -> child instanceof Button b && b.getMessage().getString().equals(label))
                        .map(child -> (Button) child).findFirst().orElseThrow();
                require(button.active, "The new-world create button must be ready");
                creating = true;
                button.onPress();
            }
            if (passed) {
                LogUtils.getLogger().info("AIRDROP_DIMENSION_STARTUP_CHECK_PASSED: actual fresh-world screen; tagged type and whitelist valid before reload; reload also valid");
                finished = true;
                client.stop();
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_DIMENSION_STARTUP_CHECK_FAILED", error);
            finished = true;
            client.stop();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
