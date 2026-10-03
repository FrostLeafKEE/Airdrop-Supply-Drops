package com.prtsnote.airdrop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Block-style entity geometry with dedicated material textures. */
final class TexturedBox {
    static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("airdrop_supply_drops", "textures/entity/" + name + ".png"); }
    static void draw(ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light,
                     float x, float y, float z, float width, float height, float depth) {
        float[][] vertices = {{x,y,z},{x+width,y,z},{x+width,y+height,z},{x,y+height,z},
                {x,y,z+depth},{x+width,y,z+depth},{x+width,y+height,z+depth},{x,y+height,z+depth}};
        int[][] faces = {{0,3,2,1},{5,6,7,4},{4,7,3,0},{1,2,6,5},{3,7,6,2},{4,0,1,5}};
        float[][] normals = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
        float[][] uv = {{0,1},{0,0},{1,0},{1,1}};
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var transform = pose.last();
        for (int face = 0; face < faces.length; face++) for (int corner = 0; corner < 4; corner++) {
            float[] point = vertices[faces[face][corner]], normal = normals[face];
            consumer.addVertex(transform.pose(), point[0], point[1], point[2])
                    .setColor(255, 255, 255, 255)
                    .setUv(uv[corner][0], uv[corner][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(transform, normal[0], normal[1], normal[2]);
        }
    }
    private TexturedBox() {}
}
