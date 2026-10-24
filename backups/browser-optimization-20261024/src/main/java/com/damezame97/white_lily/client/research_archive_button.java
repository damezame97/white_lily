package com.damezame97.white_lily.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** 真正的普通/悬停/按下/禁用状态；松开才执行，拖出取消。 */
public final class research_archive_button extends AbstractButton
{
    private final Runnable action;
    private boolean pressed;
    private List<FormattedCharSequence> lines=List.of();
    private Component last;
    public research_archive_button(int x,int y,int w,int h,Component text,Runnable action)
    { super(x,y,w,h,text); this.action=action; setTooltip(Tooltip.create(text)); }
    @Override public void onPress() { action.run(); }
    @Override public void onClick(double x,double y) { pressed=true; }
    @Override public void onRelease(double x,double y)
    {
        boolean execute=pressed && isMouseOver(x,y) && active;
        pressed=false;
        if(execute) onPress();
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial)
    {
        int state=!active?3:pressed?2:isHoveredOrFocused()?1:0;
        g.blitNineSliced(InvestigationScreen.ATLAS,getX(),getY(),width,height,3,32,18,state*32,24);
        var font=Minecraft.getInstance().font;
        if(!getMessage().equals(last)) { last=getMessage().copy(); lines=font.split(last,Math.max(12,width-8)); }
        int count=Math.min(2,lines.size()), top=getY()+(height-count*9)/2;
        for(int i=0;i<count;i++) g.drawCenteredString(font,lines.get(i),getX()+width/2,top+i*9,!active?0x91816A:0xF0DDB5);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
