package com.damezame97.white_lily.recipebook;

/** Pure geometry for the vertical effect rail; safe to verify without a client screen. */
public final class effect_bar_layout
{
    public static final int ICON = 18;
    public static final int SLOT = 20;
    public static final int FOOTER = 10;
    public static final int NORMAL_STEP = 20;
    public static final int OVERLAP_STEP = 14;

    public record Layout(int count, int step, boolean footer, int footerY, int contentHeight) { }

    public static Layout layout(int count, int top, int height)
    {
        if (count <= 0) return new Layout(0, 0, false, top, 0);
        int available = Math.max(0, height);
        int rawStep = step(count, available, false);
        boolean footer = count >= 5 || (count > 1 && rawStep < NORMAL_STEP);
        int step = step(count, available, footer);
        if (count >= 5) step = Math.min(step, OVERLAP_STEP);
        int content = ICON + step * (count - 1);
        int footerY = top + Math.max(0, height - FOOTER);
        return new Layout(count, step, footer, footerY, content);
    }

    private static int step(int count, int available, boolean footer)
    {
        if (count <= 1) return 0;
        int usable = Math.max(0, available - (footer ? FOOTER : 0));
        return Math.min(NORMAL_STEP, Math.max(0, (usable - ICON) / (count - 1)));
    }

    /** Later entries are drawn above earlier ones when they overlap. */
    public static int topmost(double mouseY, int top, int step, int count)
    {
        if (count <= 0) return -1;
        if (step <= 0) return mouseY >= top && mouseY < top + SLOT ? count - 1 : -1;
        for (int i = count - 1; i >= 0; i--)
        {
            int y = top + i * step;
            if (mouseY >= y && mouseY < y + SLOT) return i;
        }
        return -1;
    }

    public static int y(int top, int step, int index) { return top + step * index; }

    private effect_bar_layout() { }
}
