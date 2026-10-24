package com.damezame97.white_lily.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A portable two-block sleeping surface, deliberately without a respawn point. */
public final class white_sleeping_bag extends BedBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 0, 15, 3, 16);
    public white_sleeping_bag() {
        super(DyeColor.WHITE, BlockBehaviour.Properties.copy(Blocks.WHITE_WOOL).strength(0.3F).noOcclusion());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return null; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == net.minecraft.core.Direction.Axis.X
                ? Block.box(0, 0, 1, 16, 3, 15) : SHAPE;
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        // Guard before vanilla BedBlock's explosion branch, including on the client.
        if (!level.dimensionType().bedWorks() || !level.dimensionType().natural()) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("message.white_lily.sleeping_bag.invalid_dimension"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
