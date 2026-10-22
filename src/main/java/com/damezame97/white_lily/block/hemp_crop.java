package com.damezame97.white_lily.block;

import com.damezame97.white_lily.item.items;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.common.ForgeHooks;

/** 四阶段耕地作物；每阶段通过率为小麦的 3/7，总体成熟期望仍为七次小麦生长。 */
public final class hemp_crop extends CropBlock
{
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public hemp_crop() { super(Properties.copy(Blocks.WHEAT).noCollission()); }
    @Override protected IntegerProperty getAgeProperty() { return AGE; }
    @Override public int getMaxAge() { return 3; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(AGE); }
    @Override protected ItemLike getBaseSeedId() { return items.HEMP_SEEDS.get(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    { return Block.box(1, 0, 1, 15, 4 + getAge(state) * 4, 15); }

    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        // 明确要求耕地，不因 Forge 的通用 canSustainPlant 扩大玩家种植土壤。
        return level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                && level.getFluidState(pos).isEmpty() && super.canSurvive(state, level, pos);
    }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!level.isAreaLoaded(pos, 1) || level.getRawBrightness(pos, 0) < 9 || !canSurvive(state, level, pos)
                || isMaxAge(state)) return;
        float speed = getGrowthSpeed(this, level, pos);
        boolean grow = random.nextInt((int)(25.0F / speed) + 1) == 0 && random.nextInt(7) < 3;
        if (ForgeHooks.onCropsGrowPre(level, pos, state, grow))
        {
            level.setBlock(pos, getStateForAge(getAge(state) + 1), 2);
            ForgeHooks.onCropsGrowPost(level, pos, state);
        }
    }

    @Override protected int getBonemealAgeIncrease(Level level)
    { return Math.max(1, Math.round(super.getBonemealAgeIncrease(level) * 3.0F / 7.0F)); }
}
