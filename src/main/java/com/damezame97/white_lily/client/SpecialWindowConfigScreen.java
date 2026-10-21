package com.damezame97.white_lily.client;

import com.damezame97.white_lily.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** The vanilla-style configuration card opened from Forge's mod list. */
public final class SpecialWindowConfigScreen extends Screen
{
    private static final String KEY_PREFIX = "white_lily.special_window.";
    private final Screen parent;

    public SpecialWindowConfigScreen(Screen parent)
    {
        super(Component.translatable(KEY_PREFIX + "config_title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        int contentWidth = Math.min(310, width - 40);
        int left = (width - contentWidth) / 2;
        addRenderableWidget(CycleButton.onOffBuilder(Config.enableSpecialWindow)
                .withTooltip(value -> Tooltip.create(Component.translatable(KEY_PREFIX + "config_info")))
                .create(left, 62, contentWidth, 20,
                        Component.translatable(KEY_PREFIX + "config_option"),
                        (button, value) -> Config.setSpecialWindowEnabled(value)));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 36, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
        int contentWidth = Math.min(310, width - 40);
        graphics.drawWordWrap(font, Component.translatable(KEY_PREFIX + "config_info"),
                (width - contentWidth) / 2, 98, contentWidth, 0xB0B0B0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }
}
