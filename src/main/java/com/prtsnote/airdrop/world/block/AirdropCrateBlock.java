package com.prtsnote.airdrop.world.block;

import com.prtsnote.airdrop.registry.ModBlockEntities;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class AirdropCrateBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FOOD =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("food");
    public static final com.mojang.serialization.MapCodec<AirdropCrateBlock> CODEC = simpleCodec(AirdropCrateBlock::new);

    public AirdropCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOOD, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOOD);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirdropCrateBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? null
                : createTickerHelper(blockEntityType, ModBlockEntities.AIRDROP_CRATE.get(), AirdropCrateBlockEntity::serverTick);
    }

    // Since 1.20.5 the former use() is split: useItemOn runs with an item in hand, useWithoutItem with an empty hand.
    // Both must open the crate, otherwise holding any item would silently block claiming supplies.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AirdropCrateBlockEntity crate) {
            if (crate.isExpired()) {
                level.removeBlock(pos, false);
                return InteractionResult.CONSUME;
            }
            player.openMenu(crate);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AirdropCrateBlockEntity crate) {
            if (crate.isExpired()) {
                level.removeBlock(pos, false);
                return net.minecraft.world.ItemInteractionResult.CONSUME;
            }
            player.openMenu(crate);
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AirdropCrateBlockEntity crate) {
                crate.dropContents(level, pos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!level.isClientSide && blockEntity instanceof AirdropCrateBlockEntity crate && crate.eventId() != null) {
                com.prtsnote.airdrop.server.AirdropEvents.get(level.getServer()).forget(level.getServer(), crate.eventId());
            }
            if (blockEntity != null) {
                blockEntity.setRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }
}
