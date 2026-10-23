package com.damezame97.white_lily.client;

import com.damezame97.white_lily.processing.processing_menu;
import com.damezame97.white_lily.white_lily;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** 界面只读取菜单同步数据，文本由字体渲染，背景纹理不含写死的文字或数值。 */
public final class processing_screen extends AbstractContainerScreen<processing_menu>
{
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            white_lily.MODID, "textures/gui/simple_processing_table.png");
    private static final int TEXT = 0x404040;
    private static final String KEY = "screen.white_lily.processing.";

    public processing_screen(processing_menu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 212;
        inventoryLabelX = 34;
        inventoryLabelY = 118;
        titleLabelX = 8;
        titleLabelY = 6;
    }

    @Override protected void init()
    {
        super.init();
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable(KEY+"register"),button->{
            if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);
        }).bounds(leftPos+92,topPos+96,130,18).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(KEY+"register_hint"))).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        if (menu.getTotal() > 0)
        {
            int width = Mth.clamp((int) ((long) menu.getProgress() * 24 / menu.getTotal()), 0, 24);
            if (width > 0) graphics.blit(BACKGROUND, leftPos + 124, topPos + 33, 0, 220, width, 17, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        super.renderLabels(graphics, mouseX, mouseY);
        label(graphics, "tool", 20, 28);
        label(graphics, "ingredients", 86, 18);
        label(graphics, "container", 86, 53);
        label(graphics, "result", 180, 18);
        label(graphics, "returned", 158, 53);
        label(graphics, "byproduct", 202, 53);

        if (menu.getToolMax() > 0)
        {
            graphics.drawWordWrap(font, Component.translatable(KEY + "durability",
                    Math.max(0, menu.getToolMax() - menu.getToolDamage()), menu.getToolMax()), 8, 66, 64, TEXT);
        }
        String status = switch (menu.getStatus())
        {
            case 1 -> "working";
            case 2 -> "blocked";
            case 3 -> "unregistered";
            default -> "idle";
        };
        graphics.drawWordWrap(font, Component.translatable(KEY + "status." + status), 8, 85, 214,
                menu.getStatus() >= 2 ? 0xAA0000 : TEXT);
        graphics.drawString(font, Component.translatable(KEY + "progress", menu.getProgress(), menu.getTotal()),
                8, 104, TEXT, false);
    }

    private void label(GuiGraphics graphics, String name, int centerX, int y)
    {
        Component label = Component.translatable(KEY + name);
        graphics.drawString(font, label, Math.max(8, centerX - font.width(label) / 2), y, TEXT, false);
    }
}
