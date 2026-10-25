package com.damezame97.white_lily.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Modal list/detail view opened from the effect rail; it never changes effect state. */
public final class effect_list_screen extends Screen
{
    private static final String KEY = "screen.white_lily.";
    private static final int PANEL_W = 360, PANEL_H = 250, ROW_H = 22;
    private static final int DETAIL_TOP = 54, DETAIL_BOTTOM = 24, LINE_H = 10;
    private final Screen parent;
    @Nullable private ResourceLocation selected;
    private int scroll;
    private int detailScroll;

    public effect_list_screen(Screen parent)
    {
        this(parent, null);
    }

    public effect_list_screen(Screen parent, @Nullable ResourceLocation selected)
    {
        super(Component.translatable(KEY + "effect_list.title"));
        this.parent = parent;
        this.selected = selected;
    }

    private List<MobEffectInstance> effects() { return effect_sidebar.effects(); }
    private int panelW() { return Math.max(160, Math.min(PANEL_W, width - 16)); }
    private int panelH() { return Math.max(130, Math.min(PANEL_H, height - 16)); }

    private void ensureSelection(List<MobEffectInstance> effects)
    {
        if (effects.isEmpty()) { select(null); scroll = 0; return; }
        if (selected != null && effects.stream().anyMatch(instance -> selected.equals(effect_sidebar.id(instance)))) return;
        select(effect_sidebar.id(effects.get(0)));
    }

    private void select(@Nullable ResourceLocation id)
    {
        if (!Objects.equals(selected, id)) detailScroll = 0;
        selected = id;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial)
    {
        renderBackground(graphics);
        List<MobEffectInstance> effects = effects();
        ensureSelection(effects);
        int w = panelW();
        int h = panelH();
        int x = (width - w) / 2, y = (height - h) / 2;
        panel(graphics, x, y, w, h);
        graphics.drawCenteredString(font, title, x + w / 2, y + 7, 0xFFE7E2D6);

        int closeX = x + w - 20, closeY = y + 3;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 17, 0xFF2A313A);
        graphics.renderOutline(closeX, closeY, 16, 17, 0xFF66788E);
        graphics.drawCenteredString(font, "×", closeX + 8, closeY + 4, 0xFFE7E2D6);

        int listX = x + 8, listY = y + 27;
        int listW = Math.min(164, Math.max(100, w / 2 - 12));
        int listH = h - 39;
        int rows = Math.max(1, listH / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, effects.size() - rows)));
        graphics.drawString(font, Component.translatable(KEY + "effect_list.count", effects.size()),
                listX + 2, y + 18, 0xFFB8C2CE, false);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF121820);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF66788E);

        if (effects.isEmpty())
        {
            graphics.drawWordWrap(font, Component.translatable(KEY + "effect_list.empty"),
                    listX + 6, listY + 8, listW - 12, 0xFFB8C2CE);
        }
        else
        {
            MobEffectInstance chosen = chosen(effects);
            for (int row = 0; row < rows && scroll + row < effects.size(); row++)
            {
                int index = scroll + row;
                MobEffectInstance instance = effects.get(index);
                int rowY = listY + row * ROW_H;
                boolean active = effect_sidebar.id(instance).equals(selected);
                if (active)
                {
                    graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_H - 1, 0xFF26313B);
                    graphics.renderOutline(listX + 1, rowY + 1, listW - 2, ROW_H - 2, 0xFFFFD578);
                }
                effect_sidebar.drawIcon(graphics, instance, listX + 4, rowY + 2);
                int nameWidth = Math.max(20, listW - 72);
                graphics.drawString(font, font.plainSubstrByWidth(effect_sidebar.name(instance).getString(), nameWidth),
                        listX + 25, rowY + 3, 0xFFE7E2D6, false);
                Component time = effect_sidebar.duration(instance);
                graphics.drawString(font, time, listX + listW - 4 - font.width(time), rowY + 12,
                        0xFFB8C2CE, false);
            }
            if (effects.size() > rows) drawScrollbar(graphics, listX + listW - 3, listY, listH, effects.size(), rows, scroll);
            if (chosen != null) renderDetails(graphics, chosen, listX + listW + 10, listY,
                    x + w - 8 - (listX + listW + 10), listH);
        }
    }

    @Nullable private MobEffectInstance chosen(List<MobEffectInstance> effects)
    {
        if (selected == null) return effects.isEmpty() ? null : effects.get(0);
        return effects.stream().filter(instance -> selected.equals(effect_sidebar.id(instance))).findFirst().orElse(null);
    }

    private void renderDetails(GuiGraphics graphics, MobEffectInstance instance, int x, int y, int w, int h)
    {
        graphics.fill(x, y, x + w, y + h, 0xFF121820);
        graphics.renderOutline(x, y, w, h, 0xFF66788E);
        effect_sidebar.drawIcon(graphics, instance, x + 10, y + 10);
        graphics.drawString(font, effect_sidebar.name(instance), x + 34, y + 10, 0xFFE7E2D6, false);
        graphics.drawString(font, Component.translatable(KEY + "effect.duration", effect_sidebar.duration(instance)),
                x + 34, y + 23, 0xFFB8C2CE, false);
        graphics.fill(x + 8, y + 44, x + w - 8, y + 45, 0xFF66788E);
        List<FormattedCharSequence> lines = detailLines(instance, w);
        int visible = Math.max(0, h - DETAIL_TOP - DETAIL_BOTTOM);
        int content = lines.size() * LINE_H;
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, content - visible)));
        if (visible > 0)
        {
            graphics.enableScissor(x + 8, y + DETAIL_TOP, x + w - 8, y + DETAIL_TOP + visible);
            try
            {
                for (int index = 0; index < lines.size(); index++)
                {
                    int lineY = y + DETAIL_TOP + index * LINE_H - detailScroll;
                    if (lineY + LINE_H <= y + DETAIL_TOP || lineY >= y + DETAIL_TOP + visible) continue;
                    graphics.drawString(font, lines.get(index), x + 10, lineY, 0xFFB8C2CE, false);
                }
            }
            finally { graphics.disableScissor(); }
            if (content > visible) drawScrollbar(graphics, x + w - 5, y + DETAIL_TOP,
                    visible, content, visible, detailScroll);
        }
        Component source = Component.translatable(KEY + "effect_list.source").withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawString(font, source, x + 10, y + h - 17, 0xFF8E99A6, false);
    }

    private List<FormattedCharSequence> detailLines(MobEffectInstance instance, int width)
    {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : effect_sidebar.description(instance))
            lines.addAll(font.split(line, Math.max(1, width - 20)));
        return lines;
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int h, int total, int visible, int offset)
    {
        int thumb = Math.min(h, Math.max(10, h * visible / Math.max(1, total)));
        int travel = Math.max(1, h - thumb);
        int max = Math.max(1, total - visible);
        int ty = y + travel * offset / max;
        graphics.fill(x, y, x + 2, y + h, 0xFF26313B);
        graphics.fill(x, ty, x + 2, ty + thumb, 0xFFB8C2CE);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int w, int h)
    {
        graphics.fill(x, y, x + w, y + h, 0xF01E2531);
        graphics.renderOutline(x, y, w, h, 0xFF66788E);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFF8A9AAB);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        int w = panelW();
        int h = panelH();
        int x = (width - w) / 2, y = (height - h) / 2;
        if (mouseX >= x + w - 20 && mouseX < x + w - 4 && mouseY >= y + 3 && mouseY < y + 20)
        {
            onClose();
            return true;
        }
        List<MobEffectInstance> effects = effects();
        int listX = x + 8, listY = y + 27;
        int listW = Math.min(164, Math.max(100, w / 2 - 12));
        int listH = h - 39;
        int rows = Math.max(1, listH / ROW_H);
        if (button == 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH)
        {
            int row = (int) ((mouseY - listY) / ROW_H);
            int index = scroll + row;
            if (index >= 0 && index < effects.size()) select(effect_sidebar.id(effects.get(index)));
            return true;
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        List<MobEffectInstance> effects = effects();
        ensureSelection(effects);
        if (amount == 0) return true;
        int w = panelW();
        int h = panelH();
        int x = (width - w) / 2, y = (height - h) / 2;
        int listX = x + 8, listY = y + 27;
        int listW = Math.min(164, Math.max(100, w / 2 - 12));
        int listH = h - 39;
        if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH)
        {
            int rows = Math.max(1, listH / ROW_H);
            int max = Math.max(0, effects.size() - rows);
            scroll = Math.max(0, Math.min(max, scroll + (amount > 0 ? -1 : 1)));
            return true;
        }
        int detailX = listX + listW + 10;
        int detailW = x + w - 8 - detailX;
        if (mouseX >= detailX && mouseX < detailX + detailW && mouseY >= listY && mouseY < listY + listH)
        {
            MobEffectInstance instance = chosen(effects);
            if (instance != null)
            {
                int visible = Math.max(0, listH - DETAIL_TOP - DETAIL_BOTTOM);
                int max = Math.max(0, detailLines(instance, detailW).size() * LINE_H - visible);
                // Recompute against current wrapping after a resize, including before the next render.
                detailScroll = Math.max(0, Math.min(max, detailScroll));
                detailScroll = Math.max(0, Math.min(max, detailScroll + (amount > 0 ? -3 : 3) * LINE_H));
            }
        }
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose()
    {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
