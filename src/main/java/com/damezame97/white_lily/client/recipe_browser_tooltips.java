package com.damezame97.white_lily.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Keep native/Forge tooltip components and font; oversized tooltips scroll inside the screen. */
public final class recipe_browser_tooltips
{
    public static org.joml.Vector2ic position(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
                                             int width,int height,int mouseX,int mouseY,int tooltipWidth,int tooltipHeight)
    {
        var original=positioner.positionTooltip(width,height,mouseX,mouseY,tooltipWidth,tooltipHeight);
        if(!recipe_browser.tooltipSurface(Minecraft.getInstance().screen))return original;
        // Native placement puts an oversized tooltip above y=0. Start at its top; Alt+wheel then reveals the rest.
        return new org.joml.Vector2i(Math.max(4,Math.min(original.x(),width-tooltipWidth-4)),
                Math.max(8,Math.min(original.y(),height-tooltipHeight-6)));
    }
    private static Object screen;
    private static int scroll,maximum,lastX,lastY;
    private static boolean visible;
    public static void frame(){visible=false;}
    public static boolean begin(GuiGraphics g,Font font,List<ClientTooltipComponent> components,int x,int y)
    {
        var mc=Minecraft.getInstance();if(!recipe_browser.tooltipSurface(mc.screen))return false;
        int height=components.stream().mapToInt(ClientTooltipComponent::getHeight).sum()+8;
        if(height<=g.guiHeight()-20)return false;
        if(screen!=mc.screen || Math.abs(lastX-x)>2 || Math.abs(lastY-y)>2)scroll=0;
        screen=mc.screen;lastX=x;lastY=y;maximum=Math.max(0,height-g.guiHeight()+28);scroll=Math.min(scroll,maximum);visible=true;
        g.enableScissor(4,4,g.guiWidth()-4,g.guiHeight()-17);g.pose().pushPose();g.pose().translate(0,-scroll,0);return true;
    }
    public static void end(GuiGraphics g)
    {
        g.pose().popPose();g.disableScissor();g.pose().pushPose();g.pose().translate(0,0,700);
        g.fill(4,g.guiHeight()-17,g.guiWidth()-4,g.guiHeight()-3,0xE0100010);
        g.drawString(Minecraft.getInstance().font,Component.translatable(recipe_browser_index.KEY+"tooltip_scroll"),8,g.guiHeight()-14,0xFFE7E2D6,false);g.pose().popPose();
    }
    public static boolean scroll(double amount)
    {
        if(!visible||screen!=Minecraft.getInstance().screen)return false;
        scroll=Math.max(0,Math.min(maximum,scroll-(int)(amount*22)));return true;
    }
    private recipe_browser_tooltips() { }
}
