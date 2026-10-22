package com.damezame97.white_lily.block;

import com.damezame97.white_lily.infection.infection_symptom_rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public final class herb_block extends BushBlock implements BonemealableBlock
{
    public static final TagKey<Block> SOILS = TagKey.create(Registries.BLOCK, infection_symptom_rules.id("herb_soils"));
    private final boolean reproducible;
    public herb_block(boolean reproducible)
    {
        super(Properties.copy(Blocks.FERN).noCollission().instabreak().noOcclusion());
        this.reproducible = reproducible;
    }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.is(SOILS); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    { return level.getFluidState(pos).isEmpty() && level.getBlockState(pos.below()).is(SOILS); }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean client)
    { return reproducible; }
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    { return reproducible; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    { if (reproducible && state.is(this)) popResource(level, pos, new ItemStack(asItem())); }
}
