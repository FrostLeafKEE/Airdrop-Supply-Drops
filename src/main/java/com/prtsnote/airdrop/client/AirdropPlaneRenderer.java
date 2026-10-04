package com.prtsnote.airdrop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import com.prtsnote.airdrop.world.entity.AircraftAppearance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class AirdropPlaneRenderer extends EntityRenderer<AirdropPlane> {
    private static final ResourceLocation BODY = TexturedBox.texture("aircraft_body"), FRAME = TexturedBox.texture("aircraft_frame"),
            GLASS = TexturedBox.texture("aircraft_glass"), RUBBER = TexturedBox.texture("aircraft_rubber");
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/generic_7.png");
    public AirdropPlaneRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public boolean shouldRender(AirdropPlane plane, Frustum frustum, double x, double y, double z) {
        return plane.distanceToSqr(x, y, z) < 512 * 512;
    }
    @Override public void render(AirdropPlane plane, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        double visualAge = plane.visualFlightAge(partial);
        pose.mulPose(Axis.YP.rotationDegrees(plane.heading()));
        pose.translate(0, 0, visualAge - AirdropEvents.RELEASE_TICK);
        drawParts(AircraftAppearance.BODY, pose, buffers, light);
        for (float x : new float[]{-AircraftAppearance.ENGINE_X, AircraftAppearance.ENGINE_X}) {
            pose.pushPose(); pose.translate(x, AircraftAppearance.ENGINE_Y, AircraftAppearance.PROPELLER_Z);
            pose.mulPose(Axis.ZP.rotationDegrees((float) (visualAge * 45)));
            drawParts(AircraftAppearance.PROPELLER, pose, buffers, light);
            pose.popPose();
        }
        for (var lamp : AircraftAppearance.LAMPS) drawLamp(lamp, visualAge, plane.heading(), pose, buffers);
        pose.popPose();
    }

    private static void drawParts(java.util.List<AircraftAppearance.Part> parts, PoseStack pose, MultiBufferSource buffers, int light) {
        for (var material : AircraftAppearance.Material.values()) {
            ResourceLocation texture = switch (material) {
                case BODY -> BODY; case FRAME -> FRAME; case GLASS -> GLASS; case RUBBER -> RUBBER;
            };
            VertexConsumer consumer = null;
            for (var part : parts) if (part.material() == material) {
                if (consumer == null) consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
                for (var quad : part.faces()) drawTexturedQuad(consumer, pose, quad, light);
            }
        }
    }

    private void drawLamp(AircraftAppearance.Lamp lamp, double age, float heading, PoseStack pose, MultiBufferSource buffers) {
        double intensity = AircraftAppearance.intensity(lamp, age);
        if (intensity <= 0.01) return;
        int r = (lamp.rgb() >> 16) & 255, g = (lamp.rgb() >> 8) & 255, b = lamp.rgb() & 255;
        var point = lamp.position();
        pose.pushPose();
        pose.translate(point.x(), point.y(), point.z());
        var core = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        int alpha = (int) (intensity * 255);
        // Six emissive faces keep the fixture visible from above, below and behind.
        pose.pushPose();
        pose.scale(lamp.size(),lamp.size(),lamp.size());
        for (var face : AircraftAppearance.LIGHT_CORE.faces()) drawQuad(core, pose, face, r,g,b,alpha,AircraftAppearance.FULL_BRIGHT);
        pose.popPose();
        // Undo the aircraft heading before orienting the soft glow toward the camera.
        pose.mulPose(Axis.YP.rotationDegrees(-heading));
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        float radius = lamp.size() * 2.8F;
        pose.scale(radius,radius,radius);
        drawQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), pose, AircraftAppearance.LIGHT_HALO, r,g,b,(int) (intensity*135),AircraftAppearance.FULL_BRIGHT);
        pose.popPose();
    }

    private static void drawTexturedQuad(VertexConsumer consumer, PoseStack pose, AircraftAppearance.Quad quad, int light) {
        vertex(consumer,pose,quad.a(),quad.normal(),quad.ua().u(),quad.ua().v(),255,255,255,255,light);
        vertex(consumer,pose,quad.b(),quad.normal(),quad.ub().u(),quad.ub().v(),255,255,255,255,light);
        vertex(consumer,pose,quad.c(),quad.normal(),quad.uc().u(),quad.uc().v(),255,255,255,255,light);
        vertex(consumer,pose,quad.d(),quad.normal(),quad.ud().u(),quad.ud().v(),255,255,255,255,light);
    }

    private static void drawQuad(VertexConsumer consumer, PoseStack pose, AircraftAppearance.Quad quad, int r, int g, int b, int alpha, int light) {
        vertex(consumer, pose, quad.a(), quad.normal(), 0,1,r,g,b,alpha,light);
        vertex(consumer, pose, quad.b(), quad.normal(), 0,0,r,g,b,alpha,light);
        vertex(consumer, pose, quad.c(), quad.normal(), 1,0,r,g,b,alpha,light);
        vertex(consumer, pose, quad.d(), quad.normal(), 1,1,r,g,b,alpha,light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack pose, AircraftAppearance.Point point, AircraftAppearance.Point normal,
                               float u, float v, int r, int g, int b, int alpha, int light) {
        var transform = pose.last();
        consumer.addVertex(transform.pose(),point.x(),point.y(),point.z()).setColor(r,g,b,alpha)
                .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(transform,normal.x(),normal.y(),normal.z());
    }
    @Override public ResourceLocation getTextureLocation(AirdropPlane entity) { return BODY; }
}
