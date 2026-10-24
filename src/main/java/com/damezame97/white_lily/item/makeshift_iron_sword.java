package com.damezame97.white_lily.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A separate one-third-durability iron sword; never a damaged real iron sword. */
public final class makeshift_iron_sword extends SwordItem
{
    public static final String NAME = "makeshift_iron_sword";
    private final Item correspondingIronTool;

    public makeshift_iron_sword(Item correspondingIronTool)
    {
        super(makeshift_iron_tool.tier(correspondingIronTool), 3, -2.4F, new Item.Properties());
        this.correspondingIronTool = correspondingIronTool;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        makeshift_iron_tool.appendHoverText(correspondingIronTool, stack, tooltip, detail_tooltip.isExpanded(level));
    }
}
