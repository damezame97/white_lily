package com.damezame97.white_lily.item;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** 普通材料：64 堆叠，按住 Shift 展开三行双语介绍，不附加使用行为。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class basic_material_item extends Item
{
    public basic_material_item()
    {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        appendDescription(tooltip, detail_tooltip.isExpanded(level));
    }

    public void appendDescription(List<Component> tooltip, boolean expanded)
    {
        if (!expanded)
        {
            tooltip.add(detail_tooltip.hint());
            return;
        }
        for (int line = 1; line <= 3; line++)
        {
            tooltip.add(Component.translatable(getDescriptionId() + ".description." + line)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
