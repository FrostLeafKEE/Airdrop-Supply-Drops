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
    public AirdropCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOOD, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOOD);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
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

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AirdropCrateBlockEntity crate) {
            player.openMenu(crate);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AirdropCrateBlockEntity crate) {
                crate.dropContents(level, pos);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // Silk Touch and Fortune cannot recover a crate or change its salvage yield.
        return List.of(new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            // Removing a landed crate must not end a flight that has not departed yet.
            if (blockEntity != null) {
                blockEntity.setRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }
}
