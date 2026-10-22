package com.damezame97.white_lily.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** 与魔力苹果相同的按住 Shift 展开方式；服务端不读取客户端键盘状态。 */
public final class detail_tooltip
{
    public static boolean isExpanded(@javax.annotation.Nullable Level level)
    {
        return level != null && level.isClientSide && Screen.hasShiftDown();
    }

    public static Component hint()
    {
        return Component.translatable("tooltip.white_lily.hold_shift_details",
                Component.literal("Shift").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY);
    }

    private detail_tooltip()
    {
    }
}
