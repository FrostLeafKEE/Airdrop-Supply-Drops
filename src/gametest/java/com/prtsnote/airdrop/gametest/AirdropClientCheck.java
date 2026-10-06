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
    private static boolean reloading;
    private static int stage;
    private static java.util.List<String> originalPacks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("airdrop.clientCheck") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || reloading) return;
        try {
            if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                client.setScreen(new TitleScreen());
                return;
            }
            if (preview == null && client.screen instanceof TitleScreen) {
                if (originalPacks == null) originalPacks = java.util.List.copyOf(client.getResourcePackRepository().getSelectedIds());
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
                checkParachute(client);
                checkAppearances(client);
                var signal = client.getItemRenderer().getModel(com.prtsnote.airdrop.world.item.SignalTubeItem.randomStack(), null, null, 0);
                var signalQuads = signal.getQuads(null, null, RandomSource.create(0));
                if (signal == client.getModelManager().getMissingModel() || signalQuads.size() != 78
                        || signalQuads.stream().anyMatch(q -> !q.getSprite().contents().name().getPath().startsWith("item/signal_"))) {
                    throw new IllegalStateException("Signal tube geometry or material did not bake");
                }
                preview = new Preview();
                client.setScreen(preview);
            } else if (preview != null && preview.frames >= 5) {
                var path = client.gameDirectory.toPath().resolve("airdrop-model-preview-" + stage + ".png");
                try (var screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) { screenshot.writeToFile(path); }
                LogUtils.getLogger().info("AIRDROP_MODEL_CHECK_STAGE_{}: preview {}", stage, path);
                if (stage < 3) reloadNext(client);
                else {
                    LogUtils.getLogger().info("AIRDROP_CLIENT_CHECK_PASSED: independent appearance IDs, landed model selection, falling crate rendering, independent canopy/rig, 32 PBR resources, invalid-file fallback and reload restoration");
                    finished = true;
                    client.stop();
                }
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_CLIENT_CHECK_FAILED", error);
            finished = true;
            client.stop();
        }
    }

    private static void checkParachute(Minecraft client) throws Exception {
        var resources = com.prtsnote.airdrop.client.ParachuteResources.INSTANCE;
        var model = client.getModelManager().getModel(com.prtsnote.airdrop.client.ParachuteResources.CANOPY);
        if (stage == 2) {
            if (com.prtsnote.airdrop.client.ParachuteResources.isUsableCanopy(model)) throw new IllegalStateException("Broken model fixture did not become missing");
        } else {
            if (model == client.getModelManager().getMissingModel()) throw new IllegalStateException("Missing parachute canopy");
            var quads = model.getQuads(null, null, RandomSource.create(0));
            if (quads.size() != (stage == 1 ? 54 : 30)) throw new IllegalStateException("Wrong canopy after reload: " + quads.size());
            if (quads.stream().anyMatch(q -> !q.getSprite().contents().name().getNamespace().equals("airdrop_supply_drops"))) {
                throw new IllegalStateException("Parachute has a missing cloth sprite");
            }
        }
        if (resources.rig().cords().size() != (stage == 1 ? 8 : 4) ||
                Math.abs(resources.rig().openWidth() - (stage == 1 ? 4.4F : 3.8F)) > .001F) {
            throw new IllegalStateException("Wrong suspension rig after reload");
        }
        try (var reader = client.getResourceManager().getResourceOrThrow(com.prtsnote.airdrop.client.ParachuteResources.RIG).openAsReader()) {
            if (stage != 2) {
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (String field : new String[]{"cord_width", "closed_vertical_scale", "open_width"}) {
                    var bad = json.deepCopy(); bad.addProperty(field, -1);
                    try {
                        com.prtsnote.airdrop.client.ParachuteResources.parse(bad);
                        throw new IllegalStateException("Invalid rig accepted: " + field);
                    } catch (IllegalArgumentException expected) { /* correct */ }
                }
                var bad = json.deepCopy(); bad.getAsJsonArray("cords").get(0).getAsJsonObject()
                        .getAsJsonArray("canopy").set(1, new com.google.gson.JsonPrimitive(Double.NaN));
                try {
                    com.prtsnote.airdrop.client.ParachuteResources.parse(bad);
                    throw new IllegalStateException("NaN attachment accepted");
                } catch (IllegalArgumentException expected) { /* correct */ }
            }
        }
        for (String name : new String[]{"block/crate_wood", "block/crate_lid", "block/crate_metal", "block/crate_latch",
                "block/crate_food", "block/crate_mineral", "entity/aircraft_body", "entity/aircraft_frame", "entity/aircraft_glass",
                "entity/aircraft_rubber", "entity/canopy_ivory", "entity/canopy_red", "item/signal_body", "item/signal_label",
                "item/signal_metal", "item/signal_dark"}) {
            for (String suffix : new String[]{"_n", "_s"}) {
                var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("airdrop_supply_drops", "textures/" + name + suffix + ".png");
                try (var stream = client.getResourceManager().getResourceOrThrow(id).open();
                     var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                    if (image.getWidth() != image.getHeight()) throw new IllegalStateException("Wrong material dimensions: " + id);
                }
            }
        }
    }


    private static void checkAppearances(Minecraft client) {
        var resources = com.prtsnote.airdrop.client.AirdropAppearanceResources.INSTANCE;
        var mineral = com.prtsnote.airdrop.data.AirdropAppearance.MINERAL;
        var food = com.prtsnote.airdrop.data.AirdropAppearance.FOOD;
        var military = net.minecraft.resources.ResourceLocation.parse("example_airdrops:military");
        var medical = net.minecraft.resources.ResourceLocation.parse("example_airdrops:medical");
        var state = ModBlocks.AIRDROP_CRATE.get().defaultBlockState();
        var wrapper = client.getBlockRenderer().getBlockModel(state);
        if (!(wrapper instanceof com.prtsnote.airdrop.client.AirdropCrateModel)) throw new IllegalStateException("Chunk crate model has no ID selector");
        for (var id : java.util.List.of(mineral, food, military, medical)) {
            var data = net.minecraftforge.client.model.data.ModelData.builder()
                    .with(com.prtsnote.airdrop.data.AirdropAppearance.MODEL_PROPERTY, id).build();
            var selected = wrapper.getQuads(state, null, RandomSource.create(0), data, null);
            var crate = resources.crate(id);
            if (selected.size() != crate.getQuads(null, null, RandomSource.create(0)).size()) {
                throw new IllegalStateException("Landed and falling appearances disagree: " + id);
            }
            if (stage == 1 && (id.equals(military) || id.equals(medical))) {
                int expected = id.equals(military) ? 6 : 12;
                if (selected.size() != expected || !resources.isUsable(crate)) throw new IllegalStateException("Custom crate did not bake: " + id);
                int panels = id.equals(military) ? 18 : 24;
                var canopy = client.getModelManager().getModel(resources.canopy(id));
                if (canopy.getQuads(null, null, RandomSource.create(0)).size() != panels) throw new IllegalStateException("Custom canopy not independent: " + id);
                float width = id.equals(military) ? 2.8F : 4.8F;
                if (Math.abs(resources.rig(id).openWidth() - width) > .001F) throw new IllegalStateException("Custom rig not independent: " + id);
                if (!wrapper.getRenderTypes(state, RandomSource.create(0), data).contains(net.minecraft.client.renderer.RenderType.cutout())) {
                    throw new IllegalStateException("Custom crate render layer not delegated");
                }
            } else if ((id.equals(military) || id.equals(medical)) && resources.definition(id) != null) {
                throw new IllegalStateException("Removed or invalid appearance definition survived reload");
            }
        }
        if (stage == 1 && Math.abs(resources.rig(mineral).openWidth() - 4.4F) > .001F) throw new IllegalStateException("Custom rig replaced default rig");
        if (stage == 2) {
            var missing = net.minecraft.resources.ResourceLocation.parse("example_airdrops:missing");
            if (resources.crate(missing) != resources.crate(food)) throw new IllegalStateException("Missing crate model ignored requested food fallback");
            if (!resources.canopy(missing).equals(com.prtsnote.airdrop.client.ParachuteResources.CANOPY)) throw new IllegalStateException("Missing canopy has no fallback");
            if (resources.rig(missing).cords().size() != 4) throw new IllegalStateException("Missing canopy retained incompatible custom cords");
        }
        for (String bad : new String[]{"{\"format\":2,\"crate_model\":\"example:x\"}", "{\"format\":1,\"crate_model\":false}",
                "{\"format\":1,\"crate_model\":\"example:x\",\"fallback\":\"example:cycle\"}"}) {
            try {
                com.prtsnote.airdrop.client.AirdropAppearanceResources.parse(com.google.gson.JsonParser.parseString(bad).getAsJsonObject());
                throw new IllegalStateException("Malformed appearance accepted");
            } catch (IllegalArgumentException expected) { /* correct */ }
        }
    }

    private static void reloadNext(Minecraft client) {
        var repository = client.getResourcePackRepository();
        repository.reload();
        var packs = new java.util.ArrayList<>(originalPacks);
        if (stage < 2) {
            String id = stage == 0 ? "file/airdrop-parachute-example" : "file/airdrop-parachute-invalid";
            if (!repository.getAvailableIds().contains(id)) throw new IllegalStateException("Missing fixture pack: " + id);
            packs.add(id);
        }
        repository.setSelected(packs);
        reloading = true;
        client.reloadResourcePacks().whenComplete((ignored, error) -> client.execute(() -> {
            reloading = false;
            if (error != null) {
                LogUtils.getLogger().error("AIRDROP_CLIENT_CHECK_FAILED: reload", error);
                finished = true; client.stop();
            } else {
                stage++; preview = null; client.setScreen(new TitleScreen());
            }
        }));
    }

    private static final class Preview extends Screen {
        private int frames;
        Preview() { super(Component.literal("Airdrop model preview")); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fillGradient(0, 0, width, height, 0xFF182536, 0xFF0A101A);
            graphics.drawCenteredString(font, "AIRDROP / CRATES + PARACHUTE + SIGNAL TUBE / STAGE " + stage, width / 2, 22, 0xFFFFFF);
            var dummy = new com.prtsnote.airdrop.world.entity.FallingAirdrop(com.prtsnote.airdrop.registry.ModEntities.FALLING_AIRDROP.get(), null);
            var renderer = (com.prtsnote.airdrop.client.FallingAirdropRenderer) minecraft.getEntityRenderDispatcher().getRenderer(dummy);
            for (int index = 0; index < 2; index++) {
                boolean food = index == 1;
                int x = width * (index * 2 + 1) / 10;
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x, height * 0.54, 120);
                float size = Math.min(width / 10F, height / 4F);
                pose.scale(size, -size, size);
                pose.mulPose(Axis.XP.rotationDegrees(25));
                pose.mulPose(Axis.YP.rotationDegrees(-35));
                pose.translate(-0.5, 0, -0.5);
                Lighting.setupFor3DItems();
                if (stage == 1) renderer.renderCrate(net.minecraft.resources.ResourceLocation.parse(food ? "example_airdrops:medical" : "example_airdrops:military"),
                        pose, graphics.bufferSource(), 15728880);
                else minecraft.getBlockRenderer().renderSingleBlock(ModBlocks.AIRDROP_CRATE.get().defaultBlockState()
                        .setValue(AirdropCrateBlock.FOOD, food), pose, graphics.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY);
                graphics.flush();
                pose.popPose();
                graphics.drawCenteredString(font, stage == 1 ? (food ? "CUSTOM B" : "CUSTOM A") : (food ? "FOOD" : "MINERALS"),
                        x, (int) (height * 0.68), food ? 0xFFAE57 : 0x79AFFF);
            }
            for (int index = 0; index < 2; index++) {
                int x = width * (index * 2 + 5) / 10;
                var pose = graphics.pose(); pose.pushPose();
                pose.translate(x, height * .73, 120);
                float size = Math.min(width / 25F, height / 7F);
                pose.scale(size, -size, size);
                pose.mulPose(Axis.XP.rotationDegrees(25)); pose.mulPose(Axis.YP.rotationDegrees(-35));
                renderer.renderParachute(stage == 1 ? net.minecraft.resources.ResourceLocation.parse(index == 0 ? "example_airdrops:military" : "example_airdrops:medical")
                        : com.prtsnote.airdrop.data.AirdropAppearance.MINERAL, index == 0 ? 1 : .25F, 18, pose, graphics.bufferSource(), 15728880);
                graphics.flush(); pose.popPose();
                graphics.drawCenteredString(font, index == 0 ? "DEPLOYED" : "OPENING", x, (int) (height * .8), 0xDDDDDD);
            }
            var pose = graphics.pose(); pose.pushPose();
            float itemScale = Math.min(width / 100F, height / 55F);
            pose.translate(width * .9 - itemScale * 8, height * .5 - itemScale * 8, 0);
            pose.scale(itemScale, itemScale, itemScale);
            graphics.renderFakeItem(com.prtsnote.airdrop.world.item.SignalTubeItem.randomStack(), 0, 0);
            graphics.flush(); pose.popPose();
            graphics.drawCenteredString(font, "SIGNAL TUBE", (int)(width * .9), (int)(height * .8), 0xB7C58C);
            frames++;
        }
    }
}
