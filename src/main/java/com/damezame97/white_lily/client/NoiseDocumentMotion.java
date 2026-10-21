package com.damezame97.white_lily.client;

/**
 * 描述文件显隐的纯JDK状态机：先从中心展开，离开时更快收拢；切换节点时先收好旧文件。
 * 不直接持有节点/Screen/字体，所以可以独立验证快速划入划出，不会泄漏旧描述或突然换字。
 */
public final class NoiseDocumentMotion {
    private final float openSeconds;
    private final float closeSeconds;
    private String visibleId;
    private float amount;
    private int anchorX;
    private int anchorY;

    public NoiseDocumentMotion(float openSeconds, float closeSeconds) {
        if (!Float.isFinite(openSeconds) || !Float.isFinite(closeSeconds)
                || openSeconds <= 0 || closeSeconds <= 0)
            throw new IllegalArgumentException("文件动画时长必须为正数");
        this.openSeconds = openSeconds;
        this.closeSeconds = closeSeconds;
    }

    public void update(String hoveredId, float deltaSeconds, int mouseX, int mouseY) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("帧时间必须非负且有限");
        if (visibleId == null && hoveredId != null) begin(hoveredId, mouseX, mouseY);
        if (visibleId == null) return;
        if (visibleId.equals(hoveredId)) {
            amount = Math.min(1, amount + deltaSeconds / openSeconds);
        } else {
            float closingTime = amount * closeSeconds;
            if (deltaSeconds < closingTime) {
                amount = Math.max(0, amount - deltaSeconds / closeSeconds);
                return;
            }
            visibleId = null;
            amount = 0;
            if (hoveredId != null) {
                begin(hoveredId, mouseX, mouseY);
                // 换节点的一帧若较长，先用足旧文件的收拢时间，再将剩余时间用于新文件。
                // 不丢帧时间，因此卡顿/低帧率与多帧细分得到相同的显隐进度。
                amount = Math.min(1, (deltaSeconds - closingTime) / openSeconds);
            }
        }
    }

    private void begin(String id, int x, int y) {
        visibleId = id;
        amount = 0;
        // 一份文件的锚点固定，不让它在节点内部轻微移动鼠标时抖动；换节点才重新定位。
        anchorX = x;
        anchorY = y;
    }

    public String visibleId() { return visibleId; }
    public float amount() { return amount; }
    public int anchorX() { return anchorX; }
    public int anchorY() { return anchorY; }
}
