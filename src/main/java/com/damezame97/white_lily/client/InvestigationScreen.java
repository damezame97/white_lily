package com.damezame97.white_lily.client;

import com.damezame97.white_lily.client.research_client.*;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.research.research_network;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.*;

/** 动态档案界面：服务端裁剪数据、客户端显示偏好、世界坐标视图三者分离。 */
public final class InvestigationScreen extends Screen
{
    public static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("white_lily","textures/gui/research_archive.png");
    private static final String KEY=research_client.KEY;
    private static final int INK=0x392919,GREEN=0x426436,GOLD=0x99711D,RED=0x762D29,MUTED=0x7F715B,LIGHT=0xECD7AA;
    private static final Map<String,research_tree_view> VIEWS=new HashMap<>();
    private static final Map<ResourceLocation,ResourceLocation> GRAY=new HashMap<>();
    @Nullable private final Screen parent;
    private Snapshot data;
    private String category="",chapter="",selected=null;
    private final research_preferences prefs=research_preferences.get();
    private research_tree_view view;
    private List<Node> nodes=List.of();
    private Map<String,Node> byId=Map.of();
    private final Map<String,List<FormattedCharSequence>> names=new HashMap<>();
    private final Map<String,Double> spacing=new HashMap<>();
    private final List<Nav> navigation=new ArrayList<>();
    private final List<Line> lines=new ArrayList<>();
    private Rect tree=Rect.EMPTY,left=Rect.EMPTY,detail=Rect.EMPTY,detailText=Rect.EMPTY,detailTrack=Rect.EMPTY,leftTrack=Rect.EMPTY;
    private boolean icons,overlay,detailOpen=true,busy,closed;
    private double detailScroll,leftScroll;
    private int textHeight,navHeight,footerTop;
    private research_archive_button submit;
    private Popup popup;
    private boolean mouseDown,dragged;
    private int pressedButton,scrollDrag;
    private double pressX,pressY,startX,startY;
    private Node pressedNode;
    private List<FormattedCharSequence> path=List.of(),nodeTitle=List.of();
    private Component notice=Component.empty();
    private FormattedCharSequence noticeLine=FormattedCharSequence.EMPTY;

    public InvestigationScreen(@Nullable Screen parent,Snapshot data)
    { super(tr("title"));this.parent=parent;this.data=data; }
    private static Component tr(String key,Object...args) { return Component.translatable(KEY+key,args); }
    public static void clearViews() { VIEWS.clear();clearGrayIcons(); }
    public static void clearGrayIcons()
    { var manager=Minecraft.getInstance().getTextureManager();GRAY.values().forEach(manager::release);GRAY.clear(); }
    @Override protected void init()
    {
        mouseDown=false;scrollDrag=0;popup=null;
        chooseChapter(); rebuildNodes();
        if(selected==null) selected=nodes.isEmpty()?"":nodes.get(0).id();
        layout();
    }
    private void chooseChapter()
    {
        for(Category c:data.categories()) for(Chapter ch:c.chapters()) if(c.open()&&ch.id().equals(chapter))return;
        for(Category c:data.categories()) if(c.open()&&!c.chapters().isEmpty())
        {category=c.id();chapter=c.chapters().get(0).id();return;}
    }
    private void rebuildNodes()
    {
        List<Node> current=new ArrayList<>();Map<String,Node> index=new LinkedHashMap<>();
        for(Node n:data.nodes()) if(n.category().equals(category)&&n.chapter().equals(chapter)){current.add(n);index.put(n.id(),n);}
        nodes=List.copyOf(current);byId=Map.copyOf(index);names.clear();spacing.clear();
        for(Node n:nodes)
        {
            names.put(n.id(),font.split(Component.translatable(n.name()),96));
            double distance=Double.POSITIVE_INFINITY;
            for(Node other:nodes) if(other!=n) distance=Math.min(distance,Math.hypot(other.x()-n.x(),other.y()-n.y()));
            spacing.put(n.id(),distance/2);
        }
        view=VIEWS.computeIfAbsent(category+"/"+chapter,k->new research_tree_view());
        if(!view.initialized)
        {
            view.initialized=true;
            if(!nodes.isEmpty()){view.centerX=nodes.stream().mapToDouble(Node::x).average().orElse(0);view.centerY=nodes.stream().mapToDouble(Node::y).average().orElse(0);}
        }
    }
    public void update(Snapshot snapshot)
    {
        data=snapshot;busy=false;popup=null;
        if(font==null)return;
        chooseChapter();rebuildNodes();
        if(!byId.containsKey(selected)){selected="";detailOpen=false;}
        layout();
    }
    private List<Control> controls(boolean compact)
    {
        List<Control> list=new ArrayList<>();
        list.add(new Control(tr("reset"),this::resetView));
        list.add(new Control(Component.literal(Math.round(view.zoom*100)+"%"),()->{view.zoom=1;layout();}));
        list.add(new Control(tr("check"),()->{if(!busy){busy=true;research_network.requestCheck();}}));
        if(compact)list.add(new Control(tr("display"),()->displayMenu(8,footerTop-65)));
        else
        {
            list.add(new Control(tr(prefs.icons?"columns_icons":"columns_expanded"),this::toggleColumns));
            list.add(new Control(tr(prefs.overlay?"details_overlay":"details_sidebar"),this::toggleDetails));
        }
        if(data.canEdit())list.add(new Control(tr(data.editing()?"edit_off":"edit_on"),()->{busy=true;research_network.requestEditing(!data.editing(),data.revision());}));
        return list;
    }
    private int controlWidth(Control c){return Math.min(width-16,Math.max(36,font.width(c.text)+12));}
    private void layout()
    {
        if(font==null)return;
        clearWidgets();popup=null;mouseDown=false;scrollDrag=0;
        icons=prefs.icons||width<390;overlay=prefs.overlay||width<390;
        List<Control> controls=controls(false);
        if(controls.stream().mapToInt(this::controlWidth).sum()+4*(controls.size()-1)>width-16)controls=controls(true);
        int rows=1,x=8;
        for(Control c:controls){int w=controlWidth(c);if(x+w>width-8){rows++;x=8;}x+=w+4;}
        footerTop=height-18-rows*22;
        int top=data.editing()?50:38, bodyHeight=Math.max(45,footerTop-top-4);
        int leftWidth=icons?28:Mth.clamp(width/5,90,158),rightWidth=Mth.clamp(width/3,132,250);
        left=new Rect(8,top,leftWidth,bodyHeight);
        int start=left.x+left.w+4,end=width-8;
        if(detailOpen&&!selected.isEmpty()&&!overlay)end-=rightWidth+4;
        tree=new Rect(start,top,Math.max(40,end-start),bodyHeight);
        detail=new Rect(width-8-rightWidth,top,rightWidth,bodyHeight);
        int bx=8,by=footerTop+13;
        for(Control c:controls)
        {
            int w=controlWidth(c);if(bx+w>width-8){bx=8;by+=22;}
            var button=button(bx,by,w,20,c.text,c.action);button.active=!busy;bx+=w+4;
        }
        button(width-31,9,22,20,Component.literal("×"),this::onClose).setTooltip(Tooltip.create(tr("close")));
        buildNav();buildDetails();
        String cat="",ch="";
        for(Category c:data.categories())if(c.id().equals(category))
        {cat=Component.translatable(c.title()).getString();for(Chapter child:c.chapters())if(child.id().equals(chapter))ch=Component.translatable(child.title()).getString();}
        path=font.split(Component.literal(cat+" / "+ch),Math.max(40,width-62));
        for(Node node:nodes)
        {
            int labelWidth=(int)Math.min(96,spacing.get(node.id())*2*view.zoom*.9);
            names.put(node.id(),font.split(Component.translatable(node.name()),Math.max(24,labelWidth)));
        }
        notice=data.notice().isEmpty()?Component.empty():tr(data.notice(),data.noticeCount());
        int legendWidth=20;
        for(String key:List.of("complete","ready","locked"))legendWidth+=font.width(tr("legend."+key))+19;
        var wrappedNotice=font.split(notice,Math.max(20,width-legendWidth));
        noticeLine=wrappedNotice.isEmpty()?FormattedCharSequence.EMPTY:wrappedNotice.get(0);
    }
    private research_archive_button button(int x,int y,int w,int h,Component label,Runnable action)
    {return addRenderableWidget(new research_archive_button(x,y,w,h,label,action));}
    private void toggleColumns(){prefs.icons=!prefs.icons;prefs.save();layout();}
    private void toggleDetails(){prefs.overlay=!prefs.overlay;prefs.save();layout();}
    private void resetView()
    {
        if(nodes.isEmpty()){view.centerX=0;view.centerY=0;view.zoom=1;}
        else view.fit(nodes.stream().mapToDouble(Node::x).min().orElse(0),nodes.stream().mapToDouble(Node::y).min().orElse(0),
                nodes.stream().mapToDouble(Node::x).max().orElse(0),nodes.stream().mapToDouble(Node::y).max().orElse(0),tree.w,tree.h);
        layout();
    }
    private void buildNav()
    {
        navigation.clear();int y=left.y+5-(int)leftScroll,index=0;
        for(Category c:data.categories())
        {
            List<FormattedCharSequence> title=font.split(Component.translatable(c.title()),Math.max(30,left.w-30));
            int h=icons?24:Math.max(26,title.size()*10+9);
            navigation.add(new Nav(new Rect(left.x+3,y,left.w-6,h),c,null,index,title));y+=h+3;
            if(!icons&&c.open())for(Chapter ch:c.chapters())
            {
                List<FormattedCharSequence> label=font.split(Component.translatable(ch.title()),left.w-20);
                int hh=Math.max(20,label.size()*10+6);
                navigation.add(new Nav(new Rect(left.x+8,y,left.w-13,hh),c,ch,index,label));y+=hh+2;
            }
            index++;
        }
        navHeight=y-left.y+(int)leftScroll;
        leftScroll=Mth.clamp(leftScroll,0,Math.max(0,navHeight-left.h+5));
        leftTrack=navHeight>left.h?new Rect(left.x+left.w-5,left.y+3,3,left.h-6):Rect.EMPTY;
    }
    private void buildDetails()
    {
        lines.clear();textHeight=0;submit=null;Node n=byId.get(selected);
        if(!detailOpen||n==null){detailText=Rect.EMPTY;return;}
        nodeTitle=font.split(Component.translatable(n.name()),Math.max(32,detail.w-54));
        int textTop=detail.y+Math.max(40,nodeTitle.size()*10+22);
        boolean canSubmit=n.ready()&&!busy&&n.prerequisites().stream().allMatch(Requirement::complete)&&n.materials().stream().allMatch(m->m.owned()>=m.count());
        List<Node> jumpTargets=new ArrayList<>();
        for(Requirement requirement:n.prerequisites())if(!requirement.complete())
            data.nodes().stream().filter(target->target.id().equals(requirement.jump())&&!target.chapter().equals(chapter)).findFirst().ifPresent(jumpTargets::add);
        boolean hasJump=!jumpTargets.isEmpty();
        boolean showSubmit=!n.complete()&&!n.state().equals("discover");
        detailText=new Rect(detail.x+7,textTop,detail.w-16,Math.max(12,detail.y+detail.h-textTop-(showSubmit?31:8)-(hasJump?23:0)));
        button(detail.x+detail.w-22,detail.y+5,17,17,Component.literal("×"),this::closeDetail).setTooltip(Tooltip.create(tr("close_detail")));
        if(showSubmit)
        {
            submit=button(detail.x+7,detail.y+detail.h-26,detail.w-14,20,tr("submit"),()->{
                if(!busy){busy=true;submit.active=false;research_network.requestSubmit(ResourceLocation.tryParse(n.id()),data.revision());}
            });submit.active=canSubmit;
        }
        if(hasJump)button(detail.x+7,detail.y+detail.h-(showSubmit?49:25),detail.w-14,18,tr("jump_prerequisite"),()->{
            List<Entry> entries=new ArrayList<>();for(Node target:jumpTargets)entries.add(new Entry(Component.translatable(target.name()),true,()->jumpTo(target)));
            openPopup(detail.x,detail.y+detail.h-100,List.of(),entries);
        });
        if(n.state().equals("discover")){heading("discovery");text(Component.translatable(n.hint()),INK);}
        else
        {
            if(!n.intro().isEmpty())text(Component.translatable(n.intro()),INK);
            if(!n.prerequisites().isEmpty())
            {
                heading("prerequisites");
                for(Requirement p:n.prerequisites())text(tr(p.complete()?"met":"missing",Component.translatable(p.name())),p.complete()?GREEN:MUTED);
            }
            if(!n.complete())
            {
                heading("research_materials");for(Material m:n.materials())material(m,true);
                if(!canSubmit)text(tr(n.ready()?"need_materials":"need_prerequisites"),MUTED);
                text(tr("recipe_locked"),GOLD);
            }
            else
            {
                heading("archive");for(String line:n.archive())text(Component.translatable(line),INK);
                for(Recipe recipe:n.recipes())
                {
                    heading("crafting");text(tr("device",tr("device."+recipe.device())),MUTED);
                    if(!recipe.tool().isEmpty()){heading("tool");material(new Material(recipe.tool(),1,0),false);text(tr("tool_damage",recipe.toolDamage()),MUTED);text(tr("registration_hint"),GOLD);}
                    Map<String,Material> combined=new LinkedHashMap<>();
                    for(Material m:recipe.ingredients())
                    {
                        String key=m.stack().getItem()+"/"+m.stack().getTag();Material old=combined.get(key);
                        combined.put(key,new Material(m.stack(),m.count()+(old==null?0:old.count()),0));
                    }
                    combined.values().forEach(m->material(m,false));heading("produces");
                    material(new Material(recipe.result(),recipe.result().getCount(),0),false);
                    if(recipe.ticks()>0)text(tr("time",recipe.ticks()/20.0),MUTED);
                    if(!recipe.returned().isEmpty()){heading("returned");material(new Material(recipe.returned(),recipe.returned().getCount(),0),false);}
                    if(!recipe.byproduct().isEmpty()){heading("byproduct");material(new Material(recipe.byproduct(),recipe.byproduct().getCount(),0),false);}
                }
            }
        }
        detailScroll=Mth.clamp(detailScroll,0,Math.max(0,textHeight-detailText.h));
        detailTrack=textHeight>detailText.h?new Rect(detail.x+detail.w-6,detailText.y,3,detailText.h):Rect.EMPTY;
    }
    private void heading(String key){line(tr(key),ItemStack.EMPTY,GOLD,5);}
    private void text(Component value,int color){line(value,ItemStack.EMPTY,color,0);}
    private void material(Material m,boolean research)
    {line(tr(research?"material_owned":"material",m.stack().getHoverName(),research?m.owned():m.count(),m.count()),m.stack(),research&&m.owned()<m.count()?MUTED:INK,0);}
    private void line(Component text,ItemStack icon,int color,int pad)
    {
        List<FormattedCharSequence> wrapped=font.split(text,Math.max(20,detailText.w-(icon.isEmpty()?3:23)));
        int h=Math.max(icon.isEmpty()?10:20,wrapped.size()*11)+pad+4;
        lines.add(new Line(wrapped,icon,color,pad,h));textHeight+=h;
    }
    private void closeDetail(){selected="";detailOpen=false;detailScroll=0;layout();}
    private void select(Node n){selected=n.id();detailOpen=true;detailScroll=0;layout();}
    @Override public void render(GuiGraphics g,int mx,int my,float delta)
    {
        renderBackground(g);panel(g,4,4,width-8,height-8,0);
        panel(g,8,8,width-16,26,3);g.renderItem(new ItemStack(items.INVESTIGATION_HANDBOOK.get()),14,10);
        g.drawString(font,title,36,10,LIGHT,false);
        for(int i=0;i<Math.min(1,path.size());i++)g.drawString(font,path.get(i),36,23,0xC9B894,false);
        if(data.editing())
        {g.fill(8,35,width-8,47,0xFF672A24);g.drawString(font,tr("editing_banner"),12,37,0xF3DEAE,false);}
        panel(g,left.x,left.y,left.w,left.h,3);panel(g,tree.x,tree.y,tree.w,tree.h,1);
        renderNav(g,mx,my);renderTree(g);
        if(detailOpen&&byId.containsKey(selected))renderDetail(g);
        renderFooter(g);super.render(g,mx,my,delta);
        if(popup!=null)renderPopup(g,mx,my);
        else
        {
            Node hover=treeInput(mx,my)?nodeAt(mx,my):null;
            if(hover!=null)g.renderTooltip(font,List.of(Component.translatable(hover.name()).getVisualOrderText(),tr("state."+hover.state()).getVisualOrderText()),mx,my);
            if(icons)for(Nav n:navigation)if(left.contains(mx,my)&&n.rect.contains(mx,my))
                g.renderTooltip(font,List.of(Component.translatable(n.cat.title()).getVisualOrderText(),tr(n.cat.open()?"chapter_choices":"unavailable").getVisualOrderText()),mx,my);
        }
    }
    private void renderNav(GuiGraphics g,int mx,int my)
    {
        g.enableScissor(left.x+2,left.y+2,left.x+left.w-2,left.y+left.h-2);
        for(Nav n:navigation)
        {
            if(n.rect.y+n.rect.h<left.y||n.rect.y>left.y+left.h)continue;
            boolean active=n.ch==null?n.cat.id().equals(category):n.ch.id().equals(chapter);
            if(active||n.cat.open()&&n.rect.contains(mx,my))panel(g,n.rect.x,n.rect.y,n.rect.w,n.rect.h,n.ch==null?2:1);
            if(n.ch==null)g.blit(ATLAS,n.rect.x+3,n.rect.y+5,n.icon*16,96,16,16,256,256);
            if(!icons)
            {
                int y=n.rect.y+5,tx=n.rect.x+(n.ch==null?22:5);
                boolean lit=active||n.cat.open()&&n.rect.contains(mx,my);
                for(var line:n.text){g.drawString(font,line,tx,y,n.cat.open()?(n.ch==null||!lit?LIGHT:INK):0x978B78,false);y+=10;}
                if(!n.cat.open())g.blit(ATLAS,n.rect.x+n.rect.w-10,n.rect.y+5,16,80,8,8,256,256);
            }
        }
        g.disableScissor();scrollbar(g,leftTrack,leftScroll,navHeight,left.h,0xB49B68);
    }
    private int sx(double x){return tree.x+(int)Math.round(tree.w/2.0+(x-view.centerX)*view.zoom);}
    private int sy(double y){return tree.y+(int)Math.round(tree.h/2.0+(y-view.centerY)*view.zoom);}
    private void renderTree(GuiGraphics g)
    {
        g.enableScissor(tree.x+3,tree.y+3,tree.x+tree.w-3,tree.y+tree.h-3);
        int grid=Math.max(10,(int)Math.round(24*view.zoom));
        for(int x=tree.x+Math.floorMod(sx(0)-tree.x,grid);x<tree.x+tree.w-3;x+=grid)g.fill(x,tree.y+3,x+1,tree.y+tree.h-3,0x187A633C);
        for(int y=tree.y+Math.floorMod(sy(0)-tree.y,grid);y<tree.y+tree.h-3;y+=grid)g.fill(tree.x+3,y,tree.x+tree.w-3,y+1,0x187A633C);
        for(Node n:nodes)for(Requirement req:n.prerequisites())
        {
            Node p=byId.get(req.id());if(p==null)continue;
            int ax=sx(p.x()),ay=sy(p.y()),bx=sx(n.x()),by=sy(n.y());
            if(Math.max(ax,bx)<tree.x||Math.min(ax,bx)>tree.x+tree.w||Math.max(ay,by)<tree.y||Math.min(ay,by)>tree.y+tree.h)continue;
            int mid=(ax+bx)/2,c=p.complete()?0xFF4E6D3D:0xFF9C8A68;
            g.fill(Math.min(ax,mid),ay,Math.max(ax,mid)+1,ay+1,c);g.fill(mid,Math.min(ay,by),mid+1,Math.max(ay,by)+1,c);
            g.fill(Math.min(mid,bx),by,Math.max(mid,bx)+1,by+1,c);
            int sign=bx>=ax?1:-1,tip=bx-sign*(int)Math.max(4,14*view.zoom);
            for(int i=0;i<3;i++)g.fill(tip-sign*i,by-i,tip-sign*i+1,by+i+1,c);
        }
        for(Node n:nodes)
        {
            int x=sx(n.x()),y=sy(n.y());double z=view.zoom;
            if(x+60*z<tree.x||x-60*z>tree.x+tree.w||y+55*z<tree.y||y-20*z>tree.y+tree.h)continue;
            if(z<.25)
            {
                int c=n.complete()?0xFF426436:n.ready()?0xFFA4812D:0xFF777568;
                if(n.id().equals(selected))g.fill(x-4,y-4,x+4,y+4,0xFF762D29);
                if(n.ready()){for(int i=-2;i<=2;i++)g.fill(x-(2-Math.abs(i)),y+i,x+(2-Math.abs(i))+1,y+i+1,c);}
                else {g.fill(x-2,y-2,x+3,y+3,c);if(n.complete()){g.fill(x-1,y,x,y+2,0xFFEAD8AF);g.fill(x,y-1,x+2,y+1,0xFFEAD8AF);}}
                continue;
            }
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale((float)z,(float)z,1);
            int state=n.id().equals(selected)?4:n.complete()?3:n.ready()?2:1;
            g.blit(ATLAS,-14,-14,state*32,48,28,28,256,256);
            if(n.state().equals("discover")||n.state().equals("locked"))grayIcon(g,n.icon(),-8,-8);else g.renderItem(n.icon(),-8,-8);
            marker(g,n.complete()?1:n.ready()?2:0,7,7);
            g.pose().popPose();
            // 正文字形保持原生像素，避免73%等非整数变换造成中文笔画重叠。
            if(z>=.5)
            {
                List<FormattedCharSequence> label=names.get(n.id());
                for(int i=0;i<label.size();i++)g.drawString(font,label.get(i),x-font.width(label.get(i))/2,y+(int)(18*z)+10*i,n.complete()?GREEN:INK,false);
            }
        }
        g.disableScissor();
    }
    private static void grayIcon(GuiGraphics g,ItemStack stack,int x,int y)
    {
        if(stack.isEmpty())return;var mc=Minecraft.getInstance();
        var sprite=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0).getParticleIcon();
        var texture=GRAY.computeIfAbsent(sprite.contents().name(),key->{
            NativeImage source=sprite.contents().getOriginalImage(),gray=new NativeImage(sprite.contents().width(),sprite.contents().height(),true);
            for(int py=0;py<gray.getHeight();py++)for(int px=0;px<gray.getWidth();px++)
            {int c=source.getPixelRGBA(px,py),v=((c&255)*30+((c>>8)&255)*59+((c>>16)&255)*11)/100;gray.setPixelRGBA(px,py,(c&0xFF000000)|v|(v<<8)|(v<<16));}
            return mc.getTextureManager().register("white_lily_research_gray",new DynamicTexture(gray));});
        g.blit(texture,x,y,16,16,0,0,16,16,16,16);
    }
    private void renderDetail(GuiGraphics g)
    {
        panel(g,detail.x,detail.y,detail.w,detail.h,0);panel(g,detail.x+3,detail.y+3,detail.w-6,detail.h-6,1);
        Node n=byId.get(selected);boolean locked=n.state().equals("discover")||n.state().equals("locked");
        if(locked)grayIcon(g,n.icon(),detail.x+8,detail.y+10);else g.renderItem(n.icon(),detail.x+8,detail.y+10);
        for(int i=0;i<nodeTitle.size();i++)g.drawString(font,nodeTitle.get(i),detail.x+29,detail.y+8+10*i,INK,false);
        g.drawString(font,tr("state."+n.state()),detail.x+8,detailText.y-12,n.complete()?GREEN:n.ready()?GOLD:MUTED,false);
        g.fill(detail.x+7,detailText.y-3,detail.x+detail.w-8,detailText.y-2,0xFFB59D6C);
        g.enableScissor(detailText.x,detailText.y,detailText.x+detailText.w,detailText.y+detailText.h);
        int y=detailText.y-(int)detailScroll;
        for(Line line:lines)
        {
            if(y+line.h>=detailText.y&&y<detailText.y+detailText.h)
            {
                int x=detailText.x;if(!line.icon.isEmpty()){g.renderItem(line.icon,x,y+line.pad);x+=20;}
                for(int i=0;i<line.text.size();i++)g.drawString(font,line.text.get(i),x,y+line.pad+11*i,line.color,false);
            }y+=line.h;
        }
        g.disableScissor();scrollbar(g,detailTrack,detailScroll,textHeight,detailText.h,0x997D4B);
    }
    private void renderFooter(GuiGraphics g)
    {
        int x=10;for(int i:new int[]{1,2,0})
        {marker(g,i,x,footerTop+1);Component text=tr("legend."+(i==1?"complete":i==2?"ready":"locked"));g.drawString(font,text,x+10,footerTop+1,LIGHT,false);x+=font.width(text)+19;}
        if(!data.notice().isEmpty())g.drawString(font,noticeLine,x,footerTop+1,0xD8BC77,false);
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int type)
    {
        if(w<=0||h<=0)return;g.blitNineSliced(ATLAS,x,y,w,h,2,16,16,type*16,0);
        if(type==0&&w>=24&&h>=24)
        {g.blit(ATLAS,x+1,y+1,0,80,10,10,256,256);g.blit(ATLAS,x+w-11,y+1,0,80,10,10,256,256);}
    }
    private static void marker(GuiGraphics g,int kind,int x,int y){g.blit(ATLAS,x,y,16+kind*8,80,8,8,256,256);}
    private static void scrollbar(GuiGraphics g,Rect r,double scroll,int total,int visible,int color)
    {
        if(r.w==0)return;int thumb=Math.max(8,r.h*visible/Math.max(1,total));
        int y=r.y+(int)(scroll/Math.max(1,total-visible)*(r.h-thumb));g.fill(r.x,r.y,r.x+r.w,r.y+r.h,0x55473320);g.fill(r.x,y,r.x+r.w,y+thumb,0xFF000000|color);
    }
    private boolean treeInput(double x,double y){return tree.contains(x,y)&&!(detailOpen&&byId.containsKey(selected)&&detail.contains(x,y));}
    private Node nodeAt(double mx,double my)
    {
        Node best=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(Node n:nodes)
        {
            double r=Math.min(view.zoom<.25?3:14*view.zoom,spacing.get(n.id())*view.zoom*.9);
            double distance=Math.hypot(mx-sx(n.x()),my-sy(n.y()));
            if(Math.max(Math.abs(mx-sx(n.x())),Math.abs(my-sy(n.y())))<=r&&distance<bestDistance){best=n;bestDistance=distance;}
        }return best;
    }
    @Override public boolean mouseClicked(double x,double y,int button)
    {
        if(popup!=null)
        {
            Popup old=popup;popup=null;
            if(old.rect.contains(x,y)&&button==0)
            {
                int index=(int)((y-old.rowsY)/20);if(y>=old.rowsY&&index>=0&&index<old.entries.size())
                {Entry entry=old.entries.get(index);if(entry.active)entry.action.run();}
            }return true;
        }
        if(super.mouseClicked(x,y,button))return true;
        if(detailOpen&&byId.containsKey(selected)&&detail.contains(x,y))
        {if(button==0&&detailTrack.contains(x,y)){scrollDrag=1;scrollAt(y);}return true;}
        if(left.contains(x,y))
        {
            if(button==0&&leftTrack.contains(x,y)){scrollDrag=2;scrollAt(y);return true;}
            if(button==0)for(Nav n:navigation)if(n.rect.contains(x,y)&&n.cat.open())
            {if(n.ch!=null)chapter(n.cat,n.ch);else if(icons){if(n.cat.chapters().size()==1)chapter(n.cat,n.cat.chapters().get(0));else chapterMenu(n.cat,(int)x,(int)y);}return true;}
            return true;
        }
        if((button==0||button==1)&&treeInput(x,y))
        {
            Node node=nodeAt(x,y);
            if(button==1&&data.editing()&&node!=null){select(node);editMenu(node,(int)x,(int)y);return true;}
            mouseDown=true;dragged=false;pressedButton=button;pressX=x;pressY=y;startX=view.centerX;startY=view.centerY;pressedNode=node;return true;
        }return false;
    }
    private void scrollAt(double y)
    {
        if(scrollDrag==1)detailScroll=Mth.clamp((y-detailTrack.y)/Math.max(1,detailTrack.h),0,1)*Math.max(0,textHeight-detailText.h);
        else {leftScroll=Mth.clamp((y-leftTrack.y)/Math.max(1,leftTrack.h),0,1)*Math.max(0,navHeight-left.h);buildNav();}
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy)
    {
        if(popup!=null){popup=null;mouseDown=false;return true;}
        if(scrollDrag!=0){scrollAt(y);return true;}
        if(!mouseDown||pressedButton!=button)return super.mouseDragged(x,y,button,dx,dy);
        if(Math.hypot(x-pressX,y-pressY)>=4)dragged=true;
        if(dragged&&pressedNode==null&&button==0){popup=null;view.centerX=startX-(x-pressX)/view.zoom;view.centerY=startY-(y-pressY)/view.zoom;}
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button)
    {
        if(scrollDrag!=0){scrollDrag=0;return true;}
        if(mouseDown&&button==pressedButton)
        {
            mouseDown=false;
            if(!dragged&&treeInput(x,y))
            {Node node=nodeAt(x,y);if(pressedNode!=null&&node!=null&&node.id().equals(pressedNode.id()))select(node);else if(pressedNode==null&&node==null)closeDetail();}
            return true;
        }return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double amount)
    {
        if(popup!=null)return true;
        if(detailOpen&&byId.containsKey(selected)&&detail.contains(x,y)){detailScroll=Mth.clamp(detailScroll-amount*22,0,Math.max(0,textHeight-detailText.h));return true;}
        if(left.contains(x,y)){leftScroll=Mth.clamp(leftScroll-amount*22,0,Math.max(0,navHeight-left.h+5));buildNav();return true;}
        if(treeInput(x,y)){view.zoomAt(view.zoom*Math.pow(1.16,amount),x-tree.x,y-tree.y,tree.w,tree.h);layout();return true;}
        return false;
    }
    private void jumpTo(Node target)
    {
        for(Category c:data.categories())if(c.open()&&c.id().equals(target.category()))for(Chapter ch:c.chapters())if(ch.id().equals(target.chapter()))
        {chapter(c,ch);select(target);view.centerX=target.x();view.centerY=target.y();return;}
    }
    private void chapter(Category c,Chapter ch){category=c.id();chapter=ch.id();selected="";detailOpen=false;detailScroll=0;popup=null;rebuildNodes();layout();}
    private void chapterMenu(Category c,int x,int y)
    {List<Entry> entries=new ArrayList<>();for(Chapter ch:c.chapters())entries.add(new Entry(Component.translatable(ch.title()),true,()->chapter(c,ch)));openPopup(x,y,List.of(),entries);}
    private void displayMenu(int x,int y)
    {openPopup(x,y,List.of(),List.of(new Entry(tr(prefs.icons?"columns_icons":"columns_expanded"),true,this::toggleColumns),new Entry(tr(prefs.overlay?"details_overlay":"details_sidebar"),true,this::toggleDetails)));}
    private void editMenu(Node n,int x,int y)
    {
        List<Component> info=new ArrayList<>();info.add(tr("edit_target",Component.translatable(n.name())));
        info.add(tr("complete_scope",n.completeImpact().size()));for(String key:n.completeImpact())info.add(Component.translatable(key));
        info.add(tr("reset_scope",n.resetImpact().size()));for(String key:n.resetImpact())info.add(Component.translatable(key));
        long expected=data.revision();ResourceLocation id=ResourceLocation.tryParse(n.id());
        openPopup(x,y,info,List.of(new Entry(tr("direct_complete"),!n.complete()&&!busy,()->{busy=true;research_network.requestEdit(id,true,expected);}),
                new Entry(tr("direct_reset"),n.complete()&&!busy,()->{busy=true;research_network.requestEdit(id,false,expected);}),new Entry(tr("cancel"),true,()->{})));
    }
    private void openPopup(int x,int y,List<Component> info,List<Entry> entries)
    {
        int w=Math.min(218,width-16);List<FormattedCharSequence> text=new ArrayList<>();
        for(Component line:info)text.addAll(font.split(line,w-14));
        int available=Math.max(0,(height-16-entries.size()*20-10)/10);
        if(text.size()>available)text=new ArrayList<>(text.subList(0,available));
        int h=10+text.size()*10+entries.size()*20;
        x=Mth.clamp(x,8,Math.max(8,width-w-8));y=Mth.clamp(y,8,Math.max(8,height-h-8));
        popup=new Popup(new Rect(x,y,w,h),List.copyOf(text),entries,y+6+text.size()*10);
    }
    private void renderPopup(GuiGraphics g,int mx,int my)
    {
        g.pose().pushPose();g.pose().translate(0,0,400);Popup p=popup;
        panel(g,p.rect.x,p.rect.y,p.rect.w,p.rect.h,0);panel(g,p.rect.x+3,p.rect.y+3,p.rect.w-6,p.rect.h-6,1);
        int y=p.rect.y+6;for(var text:p.info){g.drawString(font,text,p.rect.x+7,y,INK,false);y+=10;}
        for(int i=0;i<p.entries.size();i++)
        {
            Entry e=p.entries.get(i);int yy=p.rowsY+i*20;boolean hover=mx>=p.rect.x+4&&mx<p.rect.x+p.rect.w-4&&my>=yy&&my<yy+20;
            if(hover&&e.active)panel(g,p.rect.x+4,yy,p.rect.w-8,19,2);
            g.drawString(font,e.label,p.rect.x+8,yy+5,e.active?INK:MUTED,false);
        }g.pose().popPose();
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers)
    { if(popup!=null){if(key==256)popup=null;return true;}return super.keyPressed(key,scan,modifiers); }
    @Override public void onClose(){closeSession();if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void removed(){closeSession();super.removed();}
    private void closeSession(){if(closed)return;closed=true;popup=null;if(Minecraft.getInstance().getConnection()!=null)research_network.requestClose();}
    @Override public boolean isPauseScreen(){return false;}
    private record Rect(int x,int y,int w,int h){static final Rect EMPTY=new Rect(0,0,0,0);boolean contains(double px,double py){return w>0&&h>0&&px>=x&&py>=y&&px<x+w&&py<y+h;}}
    private record Nav(Rect rect,Category cat,@Nullable Chapter ch,int icon,List<FormattedCharSequence> text){}
    private record Line(List<FormattedCharSequence> text,ItemStack icon,int color,int pad,int h){}
    private record Control(Component text,Runnable action){}
    private record Entry(Component label,boolean active,Runnable action){}
    private record Popup(Rect rect,List<FormattedCharSequence> info,List<Entry> entries,int rowsY){}
}
