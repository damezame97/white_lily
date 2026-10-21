package com.damezame97.white_lily.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;

/** The small, three-click disturbance on the edge of the vanilla world creation screen. */
public final class SpecialWindowPortal extends AbstractWidget {
    private static final long CLICK_WINDOW_MS = 1600;
    private final CreateWorldScreen parent;
    private final long bornAt = Util.getMillis();
    private long lastClickAt;
    private int clicks;
    private float hoverAmount;
    private long lastFrameAt = bornAt;

    public SpecialWindowPortal(CreateWorldScreen parent) {
        // The bottom button row remains unobstructed, even at the smallest vanilla GUI size.
        super(parent.width - 66, parent.height - 87, 66, 47,
                Component.translatable("white_lily.special_window.title"));
        this.parent = parent;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        positionAtEdge();
        long now = Util.getMillis();
        if (now - lastClickAt > CLICK_WINDOW_MS) clicks = 0;
        float dt = Math.min(0.1F, (now - lastFrameAt) / 1000.0F);
        lastFrameAt = now;
        hoverAmount += ((isHoveredOrFocused() ? 1.0F : 0.0F) - hoverAmount)
                * (1.0F - (float) Math.exp(-dt * 12));
        float time = (now - bornAt) / 1000.0F;
        float afterClick = Math.max(0.0F, 1.0F - (now - lastClickAt) / 500.0F);
        NoiseFieldRenderer.render(graphics, getX(), getY(), width, height, time,
                mouseX, mouseY, 0.10F + hoverAmount * 0.25F + afterClick * 0.65F
                        + clicks * 0.08F, 1.0F, true);
        // All visible feedback lives in the fragmenting field: no label, tooltip,
        // click counter, frame, or legible glyph is drawn over the disturbance.
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = Util.getMillis();
        if (now - lastClickAt > CLICK_WINDOW_MS) clicks = 0;
        lastClickAt = now;
        clicks++;
        Minecraft client = Minecraft.getInstance();
        if (clicks == 3) {
            clicks = 0;
            client.setScreen(new SpecialWindowScreen(parent));
        }
    }

    private void positionAtEdge() {
        setX(parent.width - width);
        setY(parent.height - 87);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        positionAtEdge();
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager manager) {
        // The field alone responds to clicks; this entry has no ordinary button sound.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE,
                Component.translatable("white_lily.special_window.enter_hint"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && (keyCode == 257 || keyCode == 32 || keyCode == 335)) {
            onClick(getX() + width / 2.0, getY() + height / 2.0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
