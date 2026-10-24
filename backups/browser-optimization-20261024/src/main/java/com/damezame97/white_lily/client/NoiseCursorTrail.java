package com.damezame97.white_lily.client;

import java.util.ArrayList;
import java.util.List;

/** 屏幕 GUI 坐标中的真实轨迹；不依赖 Minecraft，便于单独验证时间与断段。 */
public final class NoiseCursorTrail {
    public static final int MAX_SAMPLES = 20;
    /** 可调：离开位置后恢复成蓝色所需的秒数。 */
    public static final float DURATION_SECONDS = 1.30F;
    /** 可调：影响噪粒的半径，单位为 GUI 像素；红粒和鼠标微扰共用此范围。
     * 22 像素约为上一版 42 像素的一半半径，尾迹仍连续，不会绘制实心圆。 */
    public static final float RADIUS_GUI_PIXELS = 22.0F;
    private static final float SAMPLE_INTERVAL = DURATION_SECONDS / (MAX_SAMPLES - 2);
    private static final float MAX_TIME_GAP = 0.25F;

    /** connected 表示与前一个点连成线段；恢复焦点后的第一个点为 false。 */
    public record Sample(float x, float y, float strength, boolean connected) {
        public Sample(float x, float y, float strength) {
            this(x, y, strength, true);
        }
    }

    private record Knot(float x, float y, float bornAt, boolean connected) { }

    private final ArrayList<Knot> history = new ArrayList<>(MAX_SAMPLES);
    private Knot head;
    private float lastTime = Float.NaN;

    /**
     * 每帧调用；历史约每 72ms 保存一点，另保留每帧更新的实时端点。
     * active=false 冻结最后位置并断段，但已留下的轨迹仍按原时间衰减。
     */
    public void sample(float x, float y, float timeSeconds, boolean active) {
        if (!Float.isFinite(timeSeconds)) return;
        if (Float.isFinite(lastTime) && timeSeconds < lastTime) clear();
        lastTime = timeSeconds;
        prune(timeSeconds);
        if (!active || !Float.isFinite(x) || !Float.isFinite(y)) {
            freezeHead();
            return;
        }
        if (head == null || timeSeconds - head.bornAt > MAX_TIME_GAP) {
            freezeHead();
            head = new Knot(x, y, timeSeconds, false);
            append(head);
            return;
        }

        head = new Knot(x, y, timeSeconds, true);
        if (history.isEmpty() || timeSeconds - history.get(history.size() - 1).bornAt >= SAMPLE_INTERVAL) {
            append(head);
        }
    }

    /** 按旧到新返回，最多 20 点；静止鼠标由实时端点持续保持强度 1。 */
    public List<Sample> samples(float timeSeconds) {
        if (!Float.isFinite(timeSeconds)) return List.of();
        prune(timeSeconds);
        ArrayList<Sample> result = new ArrayList<>(MAX_SAMPLES);
        for (Knot knot : history) result.add(aged(knot, timeSeconds));
        if (head != null && (history.isEmpty() || history.get(history.size() - 1) != head)) {
            result.add(aged(head, timeSeconds));
        }
        return List.copyOf(result);
    }

    /** 窗口尺寸或 GUI 坐标系变化时由调用者清除。 */
    public void clear() {
        history.clear();
        head = null;
        lastTime = Float.NaN;
    }

    /** 与 shader 相同的线段距离计算；降级渲染也会留下连续、渐退的红粒。 */
    public static float influenceAt(List<Sample> samples, float x, float y, float radius) {
        if (samples == null || samples.isEmpty() || radius <= 0) return 0;
        float result = 0;
        Sample previous = null;
        float radiusSquared = radius * radius;
        for (Sample current : samples) {
            float dx = x - current.x;
            float dy = y - current.y;
            float distanceSquared = dx * dx + dy * dy;
            float strength = current.strength;
            if (previous != null && current.connected) {
                float sx = current.x - previous.x;
                float sy = current.y - previous.y;
                float segmentLengthSquared = sx * sx + sy * sy;
                float along = clamp(((x - previous.x) * sx + (y - previous.y) * sy)
                        / Math.max(0.0001F, segmentLengthSquared));
                dx = x - previous.x - sx * along;
                dy = y - previous.y - sy * along;
                float segmentDistance = dx * dx + dy * dy;
                float segmentStrength = previous.strength + (current.strength - previous.strength) * along;
                float fade = Math.max(0, 1 - segmentDistance / radiusSquared);
                result = Math.max(result, segmentStrength * fade * fade);
            }
            float fade = Math.max(0, 1 - distanceSquared / radiusSquared);
            result = Math.max(result, strength * fade * fade);
            previous = current;
        }
        return clamp(result);
    }

    private void freezeHead() {
        if (head != null && (history.isEmpty() || history.get(history.size() - 1) != head)) append(head);
        head = null;
    }

    private void append(Knot knot) {
        history.add(knot);
        // 为未提交的实时端点留一个位置，避免高帧率挤掉一秒前的轨迹。
        while (history.size() > MAX_SAMPLES - 1) history.remove(0);
    }

    private void prune(float timeSeconds) {
        history.removeIf(knot -> timeSeconds - knot.bornAt >= DURATION_SECONDS);
        if (head != null && timeSeconds - head.bornAt >= DURATION_SECONDS) head = null;
    }

    private static Sample aged(Knot knot, float timeSeconds) {
        float remaining = clamp(1 - Math.max(0, timeSeconds - knot.bornAt) / DURATION_SECONDS);
        float strength = remaining * remaining * (3 - 2 * remaining);
        return new Sample(knot.x, knot.y, strength, knot.connected);
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }
}
