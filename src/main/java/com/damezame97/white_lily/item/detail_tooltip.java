package com.damezame97.white_lily.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

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

    /**
     * 统一的“折叠时只显示提示、展开时追加详情”入口。
     * 石制研钵、普通材料、本轮工具和知识书共用同一个 Shift 判断与提示文案。
     */
    public static void appendDetails(List<Component> tooltip, boolean expanded, Consumer<List<Component>> details)
    {
        if (!expanded)
        {
            tooltip.add(hint());
            return;
        }
        details.accept(tooltip);
    }

    private detail_tooltip()
    {
    }
}
