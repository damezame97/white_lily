package com.damezame97.white_lily.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/** 方块物品沿用普通材料的 Shift 简介，保留正常放置行为。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class processing_table_item extends BlockItem
{
    public processing_table_item(Block block)
    {
        super(block, new Properties().stacksTo(64));
    }

    /** A dropped/creative copied table never restores a previous machine's knowledge or inventory. */
    @Override protected boolean updateCustomBlockEntityTag(net.minecraft.core.BlockPos pos,Level level,@Nullable net.minecraft.world.entity.player.Player player,ItemStack stack,net.minecraft.world.level.block.state.BlockState state)
    { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        // 外观叙述已移除；只保留自动加工、输出阻塞和进度重置等实际功能。
        basic_material_item.appendDescription(tooltip, detail_tooltip.isExpanded(level), getDescriptionId(), false, 3);
    }
}
