package com.prtsnote.airdrop.gametest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.world.block.AirdropCrateBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development-only client model check. Never packaged into the mod. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropClientCheck {
    private static Preview preview;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("airdrop.clientCheck") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null) return;
        try {
            if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                client.setScreen(new TitleScreen());
                return;
            }
            if (preview == null && client.screen instanceof TitleScreen) {
                for (boolean food : new boolean[]{false, true}) {
                    var state = ModBlocks.AIRDROP_CRATE.get().defaultBlockState().setValue(AirdropCrateBlock.FOOD, food);
                    var model = client.getBlockRenderer().getBlockModel(state);
                    if (model == client.getModelManager().getMissingModel()) throw new IllegalStateException("Missing crate model");
                    var quads = model.getQuads(state, null, RandomSource.create(0));
                    String label = food ? "crate_food" : "crate_mineral";
                    if (quads.isEmpty() || quads.stream().noneMatch(quad -> quad.getSprite().contents().name().getPath().endsWith(label))) {
                        throw new IllegalStateException("Missing appearance texture: " + label);
                    }
                }
                preview = new Preview();
                client.setScreen(preview);
            } else if (preview != null && preview.frames >= 5) {
                var path = client.gameDirectory.toPath().resolve("airdrop-crate-preview.png");
                try (var screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) { screenshot.writeToFile(path); }
                LogUtils.getLogger().info("AIRDROP_CLIENT_CHECK_PASSED: both crate models and appearance textures loaded; preview {}", path);
                finished = true;
                client.stop();
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_CLIENT_CHECK_FAILED", error);
            finished = true;
            client.stop();
        }
    }

    private static final class Preview extends Screen {
        private int frames;
        Preview() { super(Component.literal("Airdrop model preview")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fillGradient(0, 0, width, height, 0xFF182536, 0xFF0A101A);
            graphics.drawCenteredString(font, "AIRDROP / SUPPLY CRATES", width / 2, 22, 0xFFFFFF);
            for (int index = 0; index < 2; index++) {
                boolean food = index == 1;
                int x = width * (index * 2 + 1) / 4;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x, height * 0.54, 120);
                float size = Math.min(width / 5F, height / 3.5F);
                pose.scale(size, -size, size);
                pose.mulPose(Axis.XP.rotationDegrees(25));
                pose.mulPose(Axis.YP.rotationDegrees(-35));
                pose.translate(-0.5, 0, -0.5);
                Lighting.setupFor3DItems();
                minecraft.getBlockRenderer().renderSingleBlock(ModBlocks.AIRDROP_CRATE.get().defaultBlockState()
                        .setValue(AirdropCrateBlock.FOOD, food), pose, graphics.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                graphics.flush();
                pose.popPose();
                graphics.drawCenteredString(font, food ? "FOOD" : "MINERALS", x, (int) (height * 0.68), food ? 0xFFAE57 : 0x79AFFF);
            }
            frames++;
        }
    }
}
