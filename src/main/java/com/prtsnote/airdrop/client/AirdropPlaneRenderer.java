package com.prtsnote.airdrop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class AirdropPlaneRenderer extends EntityRenderer<AirdropPlane> {
    private static final ResourceLocation BODY = TexturedBox.texture("aircraft_body"), FRAME = TexturedBox.texture("aircraft_frame"),
            GLASS = TexturedBox.texture("aircraft_glass"), RUBBER = TexturedBox.texture("aircraft_rubber");
    public AirdropPlaneRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public boolean shouldRender(AirdropPlane plane, Frustum frustum, double x, double y, double z) {
        return plane.distanceToSqr(x, y, z) < 512 * 512;
    }
    @Override public void render(AirdropPlane plane, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        double visualAge = plane.visualFlightAge(partial);
        pose.mulPose(Axis.YP.rotationDegrees(plane.heading()));
        pose.translate(0, 0, visualAge - AirdropEvents.RELEASE_TICK);
        box(BODY, pose, buffers, light, -1, -0.75F, -5, 2, 1.5F, 10);
        box(FRAME, pose, buffers, light, -0.75F, -0.5F, 5, 1.5F, 1, 1.5F);
        box(GLASS, pose, buffers, light, -0.8F, 0.45F, 3, 1.6F, 0.4F, 1.8F);
        for (float side : new float[]{-1, 1}) {
            for (int window = 0; window < 3; window++) {
                box(GLASS, pose, buffers, light, side < 0 ? -1.02F : 1, 0.05F, 0.5F - window * 1.2F, 0.02F, 0.45F, 0.65F);
            }
        }
        box(FRAME, pose, buffers, light, -7, -0.2F, -0.75F, 14, 0.35F, 2.8F);
        box(FRAME, pose, buffers, light, -3.5F, 0.4F, -4.8F, 7, 0.3F, 1.5F);
        box(BODY, pose, buffers, light, -0.2F, 0.7F, -4.7F, 0.4F, 2, 1.6F);
        for (float x : new float[]{-4, 4}) {
            box(BODY, pose, buffers, light, x - 0.5F, -0.65F, 0, 1, 1, 2.6F);
            pose.pushPose(); pose.translate(x, -0.15F, 2.7F);
            pose.mulPose(Axis.ZP.rotationDegrees((float) (visualAge * 45)));
            box(RUBBER, pose, buffers, light, -1.5F, -0.08F, 0, 3, 0.16F, 0.15F);
            box(RUBBER, pose, buffers, light, -0.08F, -1.5F, 0, 0.16F, 3, 0.15F);
            pose.popPose();
        }
        pose.popPose();
    }
    private void box(ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float z, float sx, float sy, float sz) {
        TexturedBox.draw(texture, pose, buffers, light, x, y, z, sx, sy, sz);
    }
    @Override public ResourceLocation getTextureLocation(AirdropPlane entity) { return BODY; }
}
