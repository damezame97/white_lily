package com.damezame97.white_lily.recipebook;

import java.util.ArrayList;
import java.util.List;

/** Shared layout arithmetic. Coordinates are GUI logical pixels, never window pixels. */
public final class browser_layout {
    public static int percent(int value) { return Math.max(50, Math.min(150, value)); }
    public static int cell(int percent) { return Math.max(9, Math.round(18 * percent(percent) / 100F)); }
    public static int columns(int panelWidth, int cell) { return Math.max(1, (panelWidth - 8) / Math.max(1, cell)); }
    /** Compact entry either restores the persistent sidebar or opens the independent page. */
    public enum SidebarEntry { REVEAL_SIDEBAR, OPEN_PAGE }
    public static SidebarEntry sidebarEntry(boolean spaceShort)
    {
        return spaceShort ? SidebarEntry.OPEN_PAGE : SidebarEntry.REVEAL_SIDEBAR;
    }
    /** Item rows below the compressed two-row header, reserving the footer/cheat strip. */
    public static int itemRows(int panelHeight, int topOffset, int bottomReserve, int cell)
    {
        return Math.max(1, (panelHeight - topOffset - bottomReserve) / Math.max(1, cell));
    }
    public record Popup(int x, int y, int w, int h)
    {
        public boolean contains(double mouseX, double mouseY)
        {
            return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
        }
    }
    /**
     * Place a foreground menu against an anchor. Menus are right-aligned to the anchor,
     * flip above/below when needed, and never leave the current GUI.
     */
    public static Popup placePopup(int anchorX, int anchorY, int anchorW, int anchorH,
                                   int popupW, int popupH, int screenW, int screenH,
                                   boolean preferAbove)
    {
        int w = Math.max(1, Math.min(popupW, Math.max(1, screenW - 8)));
        int h = Math.max(1, Math.min(popupH, Math.max(1, screenH - 8)));
        int x = Math.max(4, Math.min(anchorX + anchorW - w, Math.max(4, screenW - w - 4)));
        int above = anchorY - h - 2;
        int below = anchorY + anchorH + 2;
        int y;
        if (preferAbove) y = above >= 4 ? above : below;
        else y = below + h <= screenH - 4 ? below : above;
        y = Math.max(4, Math.min(y, Math.max(4, screenH - h - 4)));
        return new Popup(x, y, w, h);
    }
    public static float fitScale(int preference,int diagramWidth,int diagramHeight,int width,int bodyHeight) {
        float scale=Math.min(percent(preference)/100F,Math.max(.1F,(width-24F)/Math.max(1,diagramWidth)));
        // At least one complete normal card, including readable metadata. Never rewrite the preference.
        if(bodyHeight>48)scale=Math.min(scale,Math.max(.1F,(bodyHeight-38F)/Math.max(1,diagramHeight)));
        return scale;
    }
    public record Page(int first, int end, int height) {
        public boolean contains(int index) { return index >= first && index < end; }
    }
    public static List<Page> pages(List<Integer> heights, int available, int gap) {
        List<Page> result = new ArrayList<>();
        int first = 0, used = 0;
        for (int i = 0; i < heights.size(); i++) {
            int height = Math.max(1, heights.get(i)), next = used + (i == first ? 0 : gap) + height;
            if (i > first && next > Math.max(1, available)) {
                result.add(new Page(first, i, used)); first = i; used = height;
            } else used = next;
        }
        if (first < heights.size()) result.add(new Page(first, heights.size(), used));
        if (result.isEmpty()) result.add(new Page(0, 0, 0));
        return List.copyOf(result);
    }
    public static int locate(List<Page> pages, int index) {
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).contains(index)) return i;
        return Math.max(0, pages.size() - 1);
    }
    public static double local(double mouse, int origin, float scale) { return (mouse - origin) / scale; }
    public static boolean hit(double mx, double my, int ox, int oy, float scale, int x, int y, int w, int h) {
        double lx = local(mx, ox, scale), ly = local(my, oy, scale);
        return lx >= x && ly >= y && lx < x + w && ly < y + h;
    }
    private browser_layout() { }
}
