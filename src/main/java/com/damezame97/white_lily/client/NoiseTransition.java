package com.damezame97.white_lily.client;

/**
 * 与帧率无关的视觉过渡，只依赖 JDK，便于独立验证。
 * 逻辑开关立即生效，画面用本类在约一秒内追随；快速反复点击从当前画面继续，不能跳色。
 * {@code timeSeconds} 使用同一界面打开后的单调秒数，不能混用系统日期时间。
 */
public final class NoiseTransition {
    private final float durationSeconds;
    private float from;
    private float target;
    private float startedAt;

    public NoiseTransition(float initial, float durationSeconds) {
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0)
            throw new IllegalArgumentException("过渡时长必须为正数");
        this.durationSeconds = durationSeconds;
        this.from = this.target = clamp(initial);
    }

    /** 修改目标时保留当前进度，开启途中关闭、关闭途中再开都不会发生视觉跳变。 */
    public void target(float value, float timeSeconds) {
        value = clamp(value);
        if (value == target) return;
        from = value(timeSeconds);
        target = value;
        startedAt = timeSeconds;
    }

    public float value(float timeSeconds) {
        float t = clamp((timeSeconds - startedAt) / durationSeconds);
        float smooth = t * t * (3 - 2 * t);
        return from + (target - from) * smooth;
    }

    private static float clamp(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("过渡数值必须有限");
        return Math.max(0, Math.min(1, value));
    }
}
