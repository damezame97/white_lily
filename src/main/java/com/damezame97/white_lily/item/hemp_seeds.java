package com.damezame97.white_lily.item;

import com.damezame97.white_lily.block.medical_blocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

public final class hemp_seeds extends ItemNameBlockItem
{
    public hemp_seeds() { super(medical_blocks.HEMP_CROP.get(), new Properties().stacksTo(64)); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        if (detail_tooltip.isExpanded(level))
            tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        else tooltip.add(detail_tooltip.hint());
    }
}
