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
    public FallingAirdropRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = 0.6F;
    }

    @Override
    public boolean shouldRender(FallingAirdrop entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().inflate(2, 4, 2));
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
        float width = 0.35F + 3.45F * opening;
        float height = 1.15F + 1.85F * opening;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) Math.sin((entity.tickCount + partialTick) * 0.07) * 2 * opening));
        for (int panel = 0; panel < 5; panel++) {
            float rise = (2 - Math.abs(panel - 2)) * 0.2F * opening;
            TexturedBox.draw(panel == 2 ? RED : IVORY, pose, buffers, light,
                    -width / 2 + panel * width / 5, 0, -width / 2, width / 5, 0.12F + rise, width);
        }
        pose.popPose();
        for (float x : new float[]{-1, 1}) {
            for (float z : new float[]{-1, 1}) {
                var direction = new org.joml.Vector3f(x * (width / 2 - 0.42F), height - 1, z * (width / 2 - 0.42F));
                float length = direction.length();
                pose.pushPose();
                pose.translate(x * 0.42F, 1, z * 0.42F);
                pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), direction.normalize()));
                TexturedBox.draw(IVORY, pose, buffers, light, -0.018F, 0, -0.018F, 0.036F, length, 0.036F);
                pose.popPose();
            }
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(FallingAirdrop entity) { return TextureAtlas.LOCATION_BLOCKS; }
}
