package com.damezame97.white_lily.client;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * 主菜单彩蛋的纯 JDK 状态：每次实际进入主菜单抽一次，不能每帧或 resize 时重新抽。
 * 特殊界面访问记录只存在于本次客户端进程中，不写配置或世界存档；重启后恢复 20% 随机。
 */
public final class TitleNoiseState {
    /** 【概率调试】未进入过特殊界面时的主菜单背景概率，0.20 即 20%。 */
    public static final double RANDOM_CHANCE = 0.20;

    private final DoubleSupplier random;
    private boolean titleActive;
    private boolean noiseBackground;
    private boolean visitedSpecialWindow;

    public TitleNoiseState(DoubleSupplier random) {
        this.random = Objects.requireNonNull(random);
    }

    /** added/render 的重复调用都只保留当前选择，不重新取随机数。 */
    public boolean enterTitle() {
        if (!titleActive) {
            noiseBackground = visitedSpecialWindow || random.getAsDouble() < RANDOM_CHANCE;
            titleActive = true;
        }
        return noiseBackground;
    }

    /** 真正离开主菜单时结束这一轮选择，之后回来才重新决定。 */
    public void leaveTitle() {
        titleActive = false;
        noiseBackground = false;
    }

    /** 实际打开特殊 GUI 后锁定：本次启动后续每次返回主菜单都必定显示噪域。 */
    public void visitSpecialWindow() {
        visitedSpecialWindow = true;
    }

    public boolean isTitleActive() { return titleActive; }
    public boolean isNoiseBackground() { return titleActive && noiseBackground; }
    public boolean hasVisitedSpecialWindow() { return visitedSpecialWindow; }
}
