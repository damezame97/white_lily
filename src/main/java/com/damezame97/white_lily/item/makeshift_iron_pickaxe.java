package com.damezame97.white_lily.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A separate one-third-durability iron pickaxe; never a damaged real iron pickaxe. */
public final class makeshift_iron_pickaxe extends PickaxeItem
{
    public static final String NAME = "makeshift_iron_pickaxe";
    private final Item correspondingIronTool;

    public makeshift_iron_pickaxe(Item correspondingIronTool)
    {
        super(makeshift_iron_tool.tier(correspondingIronTool), 1, -2.8F, new Item.Properties());
        this.correspondingIronTool = correspondingIronTool;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        makeshift_iron_tool.appendHoverText(correspondingIronTool, stack, tooltip, detail_tooltip.isExpanded(level));
    }
}
