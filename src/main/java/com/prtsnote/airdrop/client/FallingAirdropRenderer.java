package com.prtsnote.airdrop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.prtsnote.airdrop.world.entity.FallingAirdrop;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.world.block.AirdropCrateBlock;

public final class FallingAirdropRenderer extends EntityRenderer<FallingAirdrop> {
    private static final ResourceLocation IVORY = TexturedBox.texture("canopy_ivory"), RED = TexturedBox.texture("canopy_red");
    private final BlockRenderDispatcher blocks;
    private net.minecraft.client.resources.model.BakedModel lastCanopy;
    private boolean usableCanopy;
    public FallingAirdropRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0.6F;
    }

    @Override
    public boolean shouldRender(FallingAirdrop entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        var rig = ParachuteResources.INSTANCE.rig();
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox()
                .inflate(Math.max(2, rig.openWidth() * 1.6), rig.openHeight() + rig.openWidth() * 2.1,
                        Math.max(2, rig.openWidth() * 1.6)));
    }

    @Override
    public void render(FallingAirdrop entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(ModBlocks.AIRDROP_CRATE.get().defaultBlockState().setValue(AirdropCrateBlock.FOOD, entity.isFood()),
                pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        float opening = net.minecraft.util.Mth.clamp((entity.deploymentTicks() + partialTick - 10) / 20F, 0, 1);
        opening = opening * opening * (3 - 2 * opening);
        renderParachute(opening, entity.tickCount + partialTick, pose, buffers, light);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** Uses the current baked model after each resource reload; never caches stale atlas sprites. */
    public void renderParachute(float opening, float age, PoseStack pose, MultiBufferSource buffers, int light) {
        var rig = ParachuteResources.INSTANCE.rig();
        float width = net.minecraft.util.Mth.lerp(opening, rig.closedWidth(), rig.openWidth());
        float height = net.minecraft.util.Mth.lerp(opening, rig.closedHeight(), rig.openHeight());
        float verticalScale = net.minecraft.util.Mth.lerp(opening, rig.closedVerticalScale(), 1);
        float sway = (float) Math.sin(age * 0.07) * 2 * opening;
        var manager = net.minecraft.client.Minecraft.getInstance().getModelManager();
        var canopy = manager.getModel(ParachuteResources.CANOPY);
        if (canopy != lastCanopy) {
            lastCanopy = canopy;
            usableCanopy = ParachuteResources.isUsableCanopy(canopy);
        }
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sway));
        if (!usableCanopy) {
            // A broken resource pack must not turn the delivery's parachute invisible.
            for (int panel = 0; panel < 5; panel++) {
                float rise = (2 - Math.abs(panel - 2)) * 0.2F * opening;
                TexturedBox.draw(panel == 2 ? RED : IVORY, pose, buffers, light,
                        -width / 2 + panel * width / 5, 0, -width / 2, width / 5, 0.12F + rise, width);
            }
        } else {
            pose.scale(width, rig.openWidth() * verticalScale, width);
            pose.translate(-0.5, 0, -0.5);
            var type = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
            blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(type), null, canopy,
                    1, 1, 1, light, OverlayTexture.NO_OVERLAY, net.minecraftforge.client.model.data.ModelData.EMPTY, type);
        }
        pose.popPose();
        var rotation = new org.joml.Quaternionf().rotationZ(sway * net.minecraft.util.Mth.DEG_TO_RAD);
        for (var cord : rig.cords()) {
            var anchor = cord.canopy();
            var target = new org.joml.Vector3f((anchor.x() / 16 - 0.5F) * width,
                    anchor.y() / 16 * rig.openWidth() * verticalScale, (anchor.z() / 16 - 0.5F) * width);
            target.rotate(rotation).add(0, height, 0);
            var start = cord.crate();
            var direction = target.sub(start.x(), start.y(), start.z());
            float length = direction.length();
            if (length < 0.0001F) continue;
            pose.pushPose();
            pose.translate(start.x(), start.y(), start.z());
            pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), direction.normalize()));
            float thickness = rig.cordWidth();
            TexturedBox.draw(rig.cordTexture(), pose, buffers, light,
                    -thickness / 2, 0, -thickness / 2, thickness, length, thickness);
            pose.popPose();
        }
    }

    @Override public ResourceLocation getTextureLocation(FallingAirdrop entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
