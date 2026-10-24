package com.damezame97.white_lily.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A separate one-third-durability iron hoe; never a damaged real iron hoe. */
public final class makeshift_iron_hoe extends HoeItem
{
    public static final String NAME = "makeshift_iron_hoe";
    private final Item correspondingIronTool;

    public makeshift_iron_hoe(Item correspondingIronTool)
    {
        super(makeshift_iron_tool.tier(correspondingIronTool), -2, -1.0F, new Item.Properties());
        this.correspondingIronTool = correspondingIronTool;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        makeshift_iron_tool.appendHoverText(correspondingIronTool, stack, tooltip, detail_tooltip.isExpanded(level));
    }
}
