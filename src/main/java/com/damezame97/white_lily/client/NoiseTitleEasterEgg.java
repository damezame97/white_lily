package com.damezame97.white_lily.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 主菜单彩蛋的客户端会话入口。由 client-only TitleScreen mixin 的真实 added/removed 驱动。
 * 主菜单的 init 会在缩放、语言/资源重载时重复调用，因此不能在那里重新抽概率。
 */
public final class NoiseTitleEasterEgg {
    // 【淡入调试】仅控制黑底上噪粒的出现，不改变原版标题和按钮的淡入/点击逻辑。
    private static final float FADE_SECONDS = 0.45F;
    private static final TitleNoiseState STATE = new TitleNoiseState(
            () -> ThreadLocalRandom.current().nextDouble());
    private static long enteredAt;

    public static void enteredTitle() {
        if (!STATE.isTitleActive()) enteredAt = Util.getMillis();
        STATE.enterTitle();
    }

    public static void leftTitle() {
        STATE.leaveTitle();
    }

    /** 只能在真正显示特殊窗口后调用，构造 Screen 但被取消打开不应触发彩蛋。 */
    public static void visitedSpecialWindow() {
        STATE.visitSpecialWindow();
    }

    public static void renderBackground(GuiGraphics graphics, int width, int height) {
        // 幂等兜底：生命周期重复或其他模组调用 render 都不会每帧重新抽概率。
        enteredTitle();
        if (!STATE.isNoiseBackground()) return;
        float seconds = Math.max(0, (Util.getMillis() - enteredAt) / 1000.0F);
        float opacity = Math.min(1.0F, seconds / FADE_SECONDS);
        opacity = opacity * opacity * (3.0F - 2.0F * opacity);
        NoiseTitleRenderer.render(graphics, width, height, seconds, opacity);
        // 原版在 panorama 遮罩前启用混合，Logo 和后续菜单继续沿用该状态。
        // 通用 renderer 会在结束时关闭混合；这里恢复注入点原本的状态，保留透明边缘和淡入。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private NoiseTitleEasterEgg() { }
}
