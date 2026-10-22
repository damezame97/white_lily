package com.damezame97.white_lily.block;

import com.damezame97.white_lily.processing.processing_block_entity;
import com.damezame97.white_lily.processing.processing_registry;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class simple_processing_table extends BaseEntityBlock
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 11, 0, 16, 13, 16), Block.box(1, 0, 1, 3, 11, 3),
            Block.box(13, 0, 1, 15, 11, 3), Block.box(1, 0, 13, 3, 11, 15),
            Block.box(13, 0, 13, 15, 11, 15), Block.box(2, 2, 2, 14, 3, 14),
            Block.box(2, 2, 1, 14, 3, 2), Block.box(2, 2, 14, 14, 3, 15),
            Block.box(2, 13, 3, 7, 15, 8), Block.box(4, 14, 5, 6, 15.9, 6),
            Block.box(8, 13, 2, 14, 14.25, 10), Block.box(10.5, 8.5, 0, 12.75, 10.75, 0.25));
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    private static Map<Direction, VoxelShape> createShapes()
    {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        VoxelShape shape = SHAPE;
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
        {
            shapes.put(direction, shape);
            VoxelShape[] rotated = {Shapes.empty()};
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> rotated[0] = Shapes.or(rotated[0],
                    Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            shape = rotated[0].optimize();
        }
        return shapes;
    }

    public simple_processing_table()
    {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context)
    { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    { return SHAPES.get(state.getValue(FACING)); }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null
                && level.getBlockEntity(pos) instanceof processing_block_entity table)
            NetworkHooks.openScreen(serverPlayer, table, pos);
        return InteractionResult.CONSUME;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new processing_block_entity(pos, state); }

    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, processing_registry.TABLE_ENTITY.get(), processing_block_entity::serverTick);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving)
    {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof processing_block_entity table) table.dropContents();
        super.onRemove(state, level, pos, replacement, moving);
    }
}
