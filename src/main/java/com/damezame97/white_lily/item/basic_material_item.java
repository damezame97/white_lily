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

/** 复用普通材料的 Shift 简介；可为工具额外列出客观使用提示。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class basic_material_item extends Item
{
    private final boolean showDescriptions;
    private final int usageLines;

    public basic_material_item()
    {
        this(new Item.Properties().stacksTo(64), true, 0);
    }

    protected basic_material_item(Item.Properties properties, int usageLines)
    {
        this(properties, true, usageLines);
    }

    public basic_material_item(Item.Properties properties, boolean showDescriptions, int usageLines)
    {
        super(properties);
        this.showDescriptions = showDescriptions;
        this.usageLines = usageLines;
    }

    /** A plain material with neither atmosphere text nor functional details to fold. */
    public static basic_material_item noDetails()
    {
        return new basic_material_item(new Item.Properties().stacksTo(64), false, 0);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        appendDescription(tooltip, detail_tooltip.isExpanded(level));
    }

    public void appendDescription(List<Component> tooltip, boolean expanded)
    {
        appendDescription(tooltip, expanded, getDescriptionId(), showDescriptions, usageLines);
    }

    public static void appendDescription(List<Component> tooltip, boolean expanded, String key, int usageLines)
    {
        appendDescription(tooltip, expanded, key, true, usageLines);
    }

    public static void appendDescription(List<Component> tooltip, boolean expanded, String key,
                                         boolean showDescriptions, int usageLines)
    {
        if (!showDescriptions && usageLines <= 0) return;
        detail_tooltip.appendDetails(tooltip, expanded, lines ->
        {
            if (showDescriptions)
            {
                for (int line = 1; line <= 3; line++)
                {
                    lines.add(Component.translatable(key + ".description." + line)
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            for (int line = 1; line <= usageLines; line++)
            {
                lines.add(Component.translatable(key + ".usage." + line).withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
