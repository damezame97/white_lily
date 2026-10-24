package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** A plain modal Screen excludes container browser/JEI overlays and their input targets. */
@Mod.EventBusSubscriber(modid="white_lily",value=Dist.CLIENT)
public final class crafting_choice_client {
    private static crafting_choice.View snapshot;
    private static AbstractContainerMenu menuObject;
    private static long lastSession;
    private static boolean opening,releaseShield;
    private static int nextRequest;
    private static Component tr(String key,Object... args){return Component.translatable("screen.white_lily.crafting_choice."+key,args);}
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event){if(event.getScreen() instanceof InventoryScreen||event.getScreen() instanceof CraftingScreen){var mc=Minecraft.getInstance();if(mc.player!=null){menuObject=mc.player.containerMenu;snapshot=null;opening=true;crafting_choice_network.open(menuObject.containerId);}}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){snapshot=null;menuObject=null;lastSession=0;opening=releaseShield=false;}
    public static void accept(crafting_choice.View view){var mc=Minecraft.getInstance();if(mc.player==null||mc.player.containerMenu!=menuObject||view.menu()!=menuObject.containerId||view.session()<lastSession||opening&&view.session()<=lastSession)return;snapshot=view;lastSession=view.session();opening=false;}
    private static crafting_choice.View view(){var mc=Minecraft.getInstance();if(snapshot==null||opening||mc.player==null||mc.player.containerMenu!=menuObject||!menuObject.stillValid(mc.player))return null;
        var grid=crafting_choice.grid(menuObject);if(grid==null||grid.getContainerSize()!=snapshot.inputs().size())return null;for(int i=0;i<grid.getContainerSize();i++)if(!ItemStack.matches(grid.getItem(i),snapshot.inputs().get(i)))return null;return snapshot;}
    private static boolean screen(Screen screen){return screen instanceof InventoryScreen||screen instanceof CraftingScreen;}
    public static boolean isModal(Screen screen){return screen instanceof ChoiceScreen;}
    public static net.minecraft.resources.ResourceLocation selected(AbstractContainerMenu menu){var view=view();return view!=null&&menu==menuObject?view.selected():null;}
    public static boolean canPredict(AbstractContainerMenu menu){return selected(menu)!=null;}
    private static crafting_choice_layout.Rect anchor(AbstractContainerScreen<?> screen){var slot=screen.getMenu().getSlot(0);return crafting_choice_layout.button(screen.width,screen.height,screen.getGuiLeft()+slot.x,screen.getGuiTop()+slot.y);}
    public static void renderButton(AbstractContainerScreen<?> screen,GuiGraphics g,int mx,int my){var view=view();if(!screen(screen)||view==null||view.candidates().size()<2)return;var r=anchor(screen);panel(g,r.x(),r.y(),r.w(),r.h());g.drawCenteredString(Minecraft.getInstance().font,tr("button",view.candidates().size()),r.x()+r.w()/2,r.y()+2,0xFF333333);}
    public static boolean click(Screen screen,double mx,double my,int button){var view=view();if(button!=0||!screen(screen)||view==null||view.candidates().size()<2)return false;var container=(AbstractContainerScreen<?>)screen;if(!anchor(container).contains(mx,my))return false;releaseShield=true;recipe_browser.openModal(new ChoiceScreen(container));return true;}
    public static boolean consumeRelease(){boolean consume=releaseShield;releaseShield=false;return consume;}
    private static void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xFFC6C6C6);g.fill(x,y,x+w-1,y+1,0xFFFFFFFF);g.fill(x,y,x+1,y+h-1,0xFFFFFFFF);g.fill(x+w-1,y,x+w,y+h,0xFF373737);g.fill(x,y+h-1,x+w,y+h,0xFF373737);}
    private static void slot(GuiGraphics g,ItemStack item,int x,int y){g.fill(x,y,x+18,y+18,0xFF373737);g.fill(x+1,y+1,x+18,y+18,0xFF8B8B8B);g.fill(x+1,y+17,x+18,y+18,0xFFFFFFFF);g.renderItem(item,x+1,y+1);g.renderItemDecorations(Minecraft.getInstance().font,item,x+1,y+1);}
    private static final class ChoiceScreen extends Screen {
        final AbstractContainerScreen<?> parent;crafting_choice_layout.Popup layout;int first,request,ticks;boolean waiting;Component message=Component.empty();
        ChoiceScreen(AbstractContainerScreen<?> parent){super(tr("title"));this.parent=parent;}
        @Override protected void init(){if(parent.width!=width||parent.height!=height)parent.resize(minecraft,width,height);rebuild();}
        void rebuild(){var view=view();layout=crafting_choice_layout.popup(width,height,anchor(parent),view==null?1:view.candidates().size());clearWidgets();var r=layout.bounds();addRenderableWidget(Button.builder(Component.literal("×"),b->onClose()).bounds(r.x()+r.w()-21,r.y()+3,18,18).build());}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            // Render the real parent once with all hover targets disabled, then draw the opaque modal last.
            parent.render(g,-10000,-10000,partial);var view=view();if(view==null)return;
            var r=layout.bounds();g.pose().pushPose();g.pose().translate(0,0,400);panel(g,r.x(),r.y(),r.w(),r.h());g.drawString(font,title,r.x()+7,r.y()+7,0xFF333333,false);
            ItemStack tooltip=ItemStack.EMPTY;crafting_choice.Candidate hovered=null;first=Math.max(0,Math.min(first,Math.max(0,view.candidates().size()-layout.rows())));
            for(int row=0;row<layout.rows()&&first+row<view.candidates().size();row++){
                var candidate=view.candidates().get(first+row);int yy=r.y()+24+row*layout.rowHeight();boolean selected=candidate.id().equals(view.selected());
                g.fill(r.x()+4,yy,r.x()+r.w()-4,yy+46,0xFFAAAAAA);g.renderOutline(r.x()+4,yy,r.w()-8,46,selected?0xFF4B713B:0xFF777777);
                for(int i=0;i<view.inputs().size();i++){int sx=r.x()+10+(i%view.width())*10,sy=yy+5+(i/view.width())*10;g.pose().pushPose();g.pose().translate(sx,sy,0);g.pose().scale(.55F,.55F,1);slot(g,view.inputs().get(i),0,0);g.pose().popPose();if(mx>=sx&&mx<sx+10&&my>=sy&&my<sy+10)tooltip=view.inputs().get(i);}
                int arrow=r.x()+45,output=r.x()+70;g.fill(arrow,yy+17,arrow+17,yy+20,0xFF777777);g.drawString(font,"›",arrow+14,yy+14,0xFF777777,false);slot(g,candidate.output(),output,yy+10);
                int nameX=r.x()+94;var lines=font.split(candidate.output().getHoverName(),Math.max(20,r.w()-106));for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),nameX,yy+5+i*10,0xFF303030,false);
                if(candidate.shapeless())g.drawString(font,tr("shapeless"),nameX,yy+28,0xFF555555,false);if(selected)g.drawString(font,"✓",r.x()+r.w()-15,yy+33,0xFF355F29,false);
                if(mx>=output&&mx<output+18&&my>=yy+10&&my<yy+28)tooltip=candidate.output();
                if(mx>=r.x()+4&&mx<r.x()+r.w()-4&&my>=yy&&my<yy+46)hovered=candidate;
            }
            Component footer=waiting?tr("waiting"):message.getString().isEmpty()?tr("scroll",view.candidates().size()):message;g.drawString(font,font.plainSubstrByWidth(footer.getString(),r.w()-14),r.x()+7,r.y()+r.h()-12,0xFF555555,false);super.render(g,mx,my,partial);
            g.pose().popPose();g.pose().pushPose();g.pose().translate(0,0,200);
            if(!tooltip.isEmpty())g.renderTooltip(font,tooltip,mx,my);
            else if(hovered!=null){List<Component> lines=new ArrayList<>();lines.add(Component.literal(hovered.id().toString()));String namespace=hovered.id().getNamespace();String mod=net.minecraftforge.fml.ModList.get().getModContainerById(namespace).map(m->m.getModInfo().getDisplayName()).orElse(namespace);lines.add(tr("source",mod));for(ItemStack returned:hovered.returned())if(!returned.isEmpty())lines.add(tr("returned",returned.getHoverName(),returned.getCount()));g.renderTooltip(font,lines.stream().flatMap(line->font.split(line,Math.min(280,width-20)).stream()).toList(),mx,my);}
            g.pose().popPose();
        }
        @Override public boolean mouseClicked(double mx,double my,int button){releaseShield=true;if(!layout.bounds().contains(mx,my)){onClose();return true;}if(super.mouseClicked(mx,my,button))return true;if(waiting)return true;var view=view();int row=layout.row(mx,my,first);if(button==0&&view!=null&&row>=first&&row<view.candidates().size()){request=++nextRequest;waiting=true;ticks=0;crafting_choice_network.choose(view,request,view.candidates().get(row).id());}return true;}
        @Override public boolean mouseScrolled(double mx,double my,double amount){if(Screen.hasAltDown()&&recipe_browser_tooltips.scroll(amount))return true;var view=view();if(layout.bounds().contains(mx,my)&&view!=null)first=Math.max(0,Math.min(Math.max(0,view.candidates().size()-layout.rows()),first+(amount>0?-1:1)));return true;}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return true;}
        @Override public void tick(){var mc=Minecraft.getInstance();if(mc.player==null||mc.player.containerMenu!=parent.getMenu()||!parent.getMenu().stillValid(mc.player)){mc.setScreen(null);return;}var view=view();if(view==null){if(++ticks>20&&!opening)onClose();return;}if(waiting&&view.request()==request){waiting=false;if(view.reason()==transfer_reason.OK){onClose();return;}message=Component.translatable(view.reason().key());rebuild();}else if(waiting&&++ticks>80){waiting=false;message=tr("timeout");}if(view.candidates().size()<2&&!waiting)onClose();}
        @Override public void onClose(){var mc=Minecraft.getInstance();mc.setScreen(mc.player!=null&&mc.player.containerMenu==parent.getMenu()?parent:null);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private crafting_choice_client() { }
}
