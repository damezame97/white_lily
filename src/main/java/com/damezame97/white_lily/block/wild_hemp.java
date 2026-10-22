package com.damezame97.white_lily.block;

import com.damezame97.white_lily.white_lily;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** 成熟野生植株：没有生长、骨粉或扩散接口，也没有可种植的野生方块物品。 */
public final class wild_hemp extends BushBlock
{
    public static final TagKey<Block> SOILS = TagKey.create(Registries.BLOCK,
            new ResourceLocation(white_lily.MODID, "wild_hemp_soils"));
    public wild_hemp() { super(Properties.copy(Blocks.FERN).noCollission().instabreak().noOcclusion()); }
    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.is(SOILS); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    { return level.getFluidState(pos).isEmpty() && level.getBlockState(pos.below()).is(SOILS); }
}
