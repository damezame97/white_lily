package com.damezame97.white_lily.client;

import com.damezame97.white_lily.white_lily;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 「无效占位符」提示文本的 Shift 切换状态——<b>纯客户端</b>。
 *
 * <h2>跟 magic_apple 的「按住 Shift 展开」有什么不同</h2>
 * <p>magic_apple 是「按住才有」：松开手就回到提示语。这里按需求改成「<b>按一下切一次</b>」：
 * 按 Shift 切换两段介绍（{@code tooltip.text.1.*} ↔ {@code tooltip.text.2.*}），
 * 松手后保持切换后的状态。</p>
 *
 * <h2>怎么知道「现在看的是这件饰品」</h2>
 * <p>{@code Item#appendHoverText} 里拿不到玩家，也拿不到鼠标位置，但有一个事实可以利用：
 * <b>你正看着它的提示时，它每帧都会被调用一次</b>。所以物品那边每次都调
 * {@link #markHovered()} 记一个时间戳，这边在 Shift 按下时检查「刚才那一小段时间内有没有被标记过」，
 * 有才切换。这样不论是在玩家背包、箱子、还是别的界面里悬停，判断都成立。</p>
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class invalid_placeholder_tooltip
{
    /** 超过这么久没有被 {@link #markHovered()} 标记，就认为玩家没在看这件饰品（毫秒）。 */
    private static final long HOVER_WINDOW_MS = 250L;

    /** 当前是不是显示「切换后」的那段介绍。 */
    private static boolean switched;

    /** 最后一次渲染本物品提示的时间（{@link Util#getMillis()}）。 */
    private static long lastHoverMs = Long.MIN_VALUE;

    /** 切换后的那段介绍现在是不是显示着。 */
    public static boolean isSwitched()
    {
        return switched;
    }

    /** 由 {@code invalid_placeholder#appendHoverText} 每帧调用。 */
    public static void markHovered()
    {
        lastHoverMs = Util.getMillis();
    }

    /** Shift 按下时切换；只在「刚看过这件物品的提示」时才理会。 */
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event)
    {
        // 只认按下那一下，忽略长按产生的 REPEAT，免得一直闪
        if (event.getAction() != GLFW.GLFW_PRESS)
        {
            return;
        }

        if (event.getKey() != GLFW.GLFW_KEY_LEFT_SHIFT && event.getKey() != GLFW.GLFW_KEY_RIGHT_SHIFT)
        {
            return;
        }

        if (Util.getMillis() - lastHoverMs > HOVER_WINDOW_MS)
        {
            return;
        }

        switched = !switched;
    }

    private invalid_placeholder_tooltip()
    {
    }
}
