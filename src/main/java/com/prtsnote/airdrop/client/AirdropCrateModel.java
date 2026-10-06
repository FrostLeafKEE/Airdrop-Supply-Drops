package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.data.AirdropAppearance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

import java.util.List;

/** Keeps landed crates in normal chunk rendering, including ambient occlusion and block PBR. */
public final class AirdropCrateModel extends BakedModelWrapper<BakedModel> {
    private AirdropCrateModel(BakedModel original) { super(original); }
    public static void wrap(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (boolean food : new boolean[]{false, true}) {
            var id = new ModelResourceLocation(new net.minecraft.resources.ResourceLocation("airdrop_supply_drops:airdrop_crate"), "food=" + food);
            var original = event.getModels().get(id);
            if (original != null) event.getModels().put(id, new AirdropCrateModel(original));
        }
    }
    private BakedModel selected(ModelData data) {
        var id = data.get(AirdropAppearance.MODEL_PROPERTY);
        return id == null ? originalModel : AirdropAppearanceResources.INSTANCE.crate(id);
    }
    @Override public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) { return data; }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        return selected(data).getQuads(state, side, random, data, type);
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return selected(data).getRenderTypes(state, random, data);
    }
    @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return selected(data).getParticleIcon(data); }
}
