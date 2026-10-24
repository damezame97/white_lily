from pathlib import Path
p=Path('src/main/java/com/damezame97/white_lily/client/recipe_browser.java')
s=p.read_text(encoding='utf-8')
s=s.replace('import com.damezame97.white_lily.recipebook.browser_history;', 'import com.damezame97.white_lily.recipebook.browser_history;\nimport com.damezame97.white_lily.recipebook.browser_layout;\nimport com.damezame97.white_lily.recipebook.browser_display.*;')
# List controls retain normal GUI size, while cell pitch changes with its own preference.
s=s.replace('int lastColumns=-1,lastRows=-1;', 'int lastColumns=-1,lastRows=-1,cellSize=18;')
s=s.replace('columns = Math.max(1, (w - 8) / 18); rows = Math.max(1, (h - 84) / 18);', 'cellSize=browser_layout.cell(recipe_browser_preferences.itemScale());\n            columns = Math.max(1, (w - 8) / cellSize); rows = Math.max(1, (h - 102) / cellSize);')
s=s.replace('if(lastColumns!=columns||lastRows!=rows){lastColumns=columns;lastRows=rows;rebuildGrid();}', '''if(lastColumns!=columns||lastRows!=rows){ItemStack anchor=firstItem();lastColumns=columns;lastRows=rows;rebuildGrid();locateItem(anchor);}''')
start=s.index('        void refreshFilter()')
s=s[:start]+'''        ItemStack firstItem(){return page>=0&&page<grid.size()?grid.get(page).stream().map(GridCell::stack).filter(stack->!stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY):ItemStack.EMPTY;}
        void locateItem(ItemStack anchor){if(anchor.isEmpty())return;for(int i=0;i<grid.size();i++)for(var cell:grid.get(i))if(ItemStack.isSameItemSameTags(anchor,cell.stack())){page=i;listPage=page;return;}}
        void scaleItems(int value){ItemStack anchor=firstItem();recipe_browser_preferences.itemScale(value);lastColumns=-1;layout();locateItem(anchor);}
'''+s[start:]
s=s.replace('g.fill(x,y,x+w,y+63,0xA01E2531)', 'g.fill(x,y,x+w,y+81,0xA01E2531)')
s=s.replace('            for(GridCell cell:grid.get(page))\n            {\n                int sx=x+4+(cell.cell()%columns)*18,sy=y+65+(cell.cell()/columns)*18;', '''            text(g,tr("item_scale",recipe_browser_preferences.itemScale()),x+4,y+65,Math.max(8,w-40),TEXT);
            g.drawString(Minecraft.getInstance().font,"−",x+w-32,y+65,TEXT,false);g.drawString(Minecraft.getInstance().font,"+",x+w-13,y+65,TEXT,false);
            for(GridCell cell:grid.get(page))
            {
                int sx=x+4+(cell.cell()%columns)*cellSize,sy=y+83+(cell.cell()/columns)*cellSize;''')
s=s.replace('if(inside(mx,my,sx,sy,18,18)){hovered=stack;g.fill(sx,sy,sx+18,sy+18,0x805B6674);}\n                g.renderItem(stack,sx+1,sy+1);\n                if(recipe_browser_preferences.contains(stack))g.fill(sx+13,sy+1,sx+17,sy+4,GOLD);', '''if(inside(mx,my,sx,sy,cellSize,cellSize)){hovered=stack;g.fill(sx,sy,sx+cellSize,sy+cellSize,0x805B6674);}
                g.pose().pushPose();g.pose().translate(sx,sy,0);float iconScale=cellSize/18F;g.pose().scale(iconScale,iconScale,1);g.renderItem(stack,1,1);g.renderItemDecorations(Minecraft.getInstance().font,stack,1,1);g.pose().popPose();
                if(recipe_browser_preferences.contains(stack))g.fill(sx+cellSize-5,sy+1,sx+cellSize-1,sy+3,GOLD);''')
s=s.replace('y + 67, w - 10', 'y + 85, w - 10')
s=s.replace('if(inside(mx,my,x,y+43,w,18))g.renderTooltip', 'if(inside(mx,my,x,y+63,w,18))g.renderTooltip(Minecraft.getInstance().font,tr("scale_help"),mx,my);\n            else if(inside(mx,my,x,y+43,w,18))g.renderTooltip')
s=s.replace('            for(GridCell cell:grid.get(page))\n            {\n                int sx=x+4+cell.cell()%columns*18,sy=y+65+cell.cell()/columns*18;', '''            if(inside(mx,my,x,y+63,w,18)){scaleItems(mx>=x+w-20?recipe_browser_preferences.itemScale()+10:mx>=x+w-40?recipe_browser_preferences.itemScale()-10:100);return true;}
            for(GridCell cell:grid.get(page))
            {
                int sx=x+4+cell.cell()%columns*cellSize,sy=y+83+cell.cell()/columns*cellSize;''')
s=s.replace('!cell.stack().isEmpty()&&inside(mx,my,sx,sy,18,18)', '!cell.stack().isEmpty()&&inside(mx,my,sx,sy,cellSize,cellSize)')
s=s.replace('my>=y+63&&my<y+h-18', 'my>=y+81&&my<y+h-18').replace('x+4+cell.cell()%columns*18,y+65+cell.cell()/columns*18,18,18', 'x+4+cell.cell()%columns*cellSize,y+83+cell.cell()/columns*cellSize,cellSize,cellSize')
start=s.index('    private static final class RecipeScreen extends Screen')
end=s.index('    private recipe_browser()',start)
s=s[:start]+'''    /** A single modal screen; history stores data, never a chain of recipe screens. */
    private static final class RecipeScreen extends Screen
    {
        private static final int INK=0xFF383838,MUTED=0xFF595959;
        final Screen parent;
        ItemStack subject;
        boolean uses,all,waiting;
        final browser_history history=new browser_history();
        List<Display> matches=List.of(),visible=List.of(),lastSource=List.of();
        List<ResourceLocation> categories=List.of();
        List<browser_layout.Page> pages=List.of(new browser_layout.Page(0,0,0));
        List<Card> cards=List.of();
        List<Button> fills=new ArrayList<>();
        Button back,categoryButton,previous,next,scaleButton;
        int category,recipe,page,x,y,w,h,bodyTop,bodyBottom,scroll;
        int request,waitTicks;
        static int nextRequest;
        ResourceLocation waitingRecipe;
        long revision=Long.MIN_VALUE,cycle=System.currentTimeMillis()/1000;
        float scale=1;
        DisplaySlot hovered;
        ItemStack hoveredStack=ItemStack.EMPTY;
        Component notice=Component.empty();
        record Card(Display display,int index,int ox,int oy,int diagramHeight,int height,int plusX,int plusY,List<net.minecraft.util.FormattedCharSequence> lines){}

        RecipeScreen(Screen parent,ItemStack subject,boolean uses,boolean all){super(tr("title"));this.parent=parent;this.subject=subject;this.uses=uses;this.all=all;}
        @Override public Component getTitle(){return tr(all?"all":uses?"uses":"recipes",subject.getHoverName());}
        @Override protected void init(){refresh();}
        browser_history.Query snapshot(){return new browser_history.Query(subject,uses,all,categories.isEmpty()?null:categories.get(category),current()==null?null:current().recipe().getId(),scroll,search,listPage,favoritesOnly);}
        void navigate(ItemStack target,boolean uses,boolean all){history.push(snapshot(),target,uses,all);subject=target.copy();this.uses=uses;this.all=all;category=recipe=scroll=0;notice=Component.empty();refresh();}
        void back(){var q=history.pop();if(q==null)return;subject=q.subject().copy();uses=q.uses();all=q.all();search=q.search();listPage=q.listPage();favoritesOnly=q.favorites();category=recipe=0;refresh();int cat=categories.indexOf(q.category());if(cat>=0)category=cat;select();boolean found=false;for(int i=0;i<visible.size();i++)if(visible.get(i).recipe().getId().equals(q.recipe())){recipe=i;found=true;break;}scroll=q.scroll();notice=found?Component.empty():tr("history_unavailable");rebuild();}
        void refresh(){ResourceLocation anchor=current()==null?null:current().recipe().getId(),cat=categories.isEmpty()?null:categories.get(Math.min(category,categories.size()-1));lastSource=recipe_browser_index.recipes();revision=recipe_browser_index.revision();matches=all?lastSource:recipe_browser_index.find(subject,uses);categories=matches.stream().map(Display::category).distinct().sorted().toList();category=Math.max(0,categories.indexOf(cat));select();if(anchor!=null)for(int i=0;i<visible.size();i++)if(visible.get(i).recipe().getId().equals(anchor)){recipe=i;break;}rebuild();}
        void select(){visible=categories.isEmpty()?List.of():matches.stream().filter(d->d.category().equals(categories.get(category))).toList();recipe=Math.max(0,Math.min(recipe,visible.size()-1));}
        Display current(){return visible.isEmpty()?null:visible.get(Math.min(recipe,visible.size()-1));}
        AbstractContainerScreen<?> container(){return rootContainer(this);}
        transfer_reason reasonFor(Display d){var mc=Minecraft.getInstance();var root=container();if(root==null||mc.player==null||mc.player.containerMenu!=root.getMenu()||!root.getMenu().stillValid(mc.player))return transfer_reason.MENU_INVALID;if(d==null)return transfer_reason.DATA_CHANGED;if(d.recipe() instanceof processing_recipe){if(!(root.getMenu() instanceof processing_menu))return transfer_reason.WRONG_DEVICE;if(!recipe_browser_index.canFillProcessing(d.recipe().getId()))return transfer_reason.UNREGISTERED;}return browser_transfer.preview(mc.player,d.recipe());}
        int diagramHeight(Display d){int bottom=d.slots().stream().mapToInt(s->s.y()+20).max().orElse(60);if(d.recipe() instanceof CraftingRecipe)bottom=Math.max(bottom,72);if(d.recipe() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe)bottom=Math.max(bottom,78);return bottom+4;}
        int diagramWidth(Display d){return Math.max(142,d.slots().stream().mapToInt(s->s.x()+22).max().orElse(142))+26;}
        List<net.minecraft.util.FormattedCharSequence> lines(Display d,int width){var text=Component.empty();for(var n:d.notes()){if(!text.getString().isEmpty())text.append(" · ");text.append(n);}return font.split(text,Math.max(35,width));}
        void rebuild(){
            clearWidgets();fills.clear();
            int maxWidth=visible.stream().mapToInt(this::diagramWidth).max().orElse(168);
            float preferred=recipe_browser_preferences.recipeScale()/100F;
            scale=Math.min(preferred,Math.max(.1F,(width-24F)/maxWidth));
            w=Math.min(Math.max(220,Math.round(maxWidth*scale)+20),Math.max(40,width-8));
            h=Math.max(80,Math.min(440,height-8));x=(width-w)/2;y=(height-h)/2;bodyTop=y+65;bodyBottom=y+h-40;
            // Compact controls are a temporary layout choice; saved scales are never overwritten.
            categoryButton=addRenderableWidget(Button.builder(current()==null?tr("empty_recipes"):current().title(),b->{category=(category+1)%Math.max(1,categories.size());recipe=scroll=0;select();rebuild();}).bounds(x+5,y+23,Math.max(30,w-10),18).build());categoryButton.active=categories.size()>1;
            addRenderableWidget(Button.builder(Component.literal("×"),b->onClose()).bounds(x+w-22,y+3,18,18).build());
            addRenderableWidget(Button.builder(Component.literal("−"),b->scaleRecipe(recipe_browser_preferences.recipeScale()-10)).bounds(x+5,y+44,18,18).build());
            scaleButton=addRenderableWidget(Button.builder(tr("recipe_scale",recipe_browser_preferences.recipeScale()),b->scaleRecipe(100)).bounds(x+26,y+44,Math.max(24,w-52),18).build());
            addRenderableWidget(Button.builder(Component.literal("+"),b->scaleRecipe(recipe_browser_preferences.recipeScale()+10)).bounds(x+w-23,y+44,18,18).build());
            back=addRenderableWidget(Button.builder(tr("history_back"),b->back()).bounds(x+5,y+h-22,46,18).build());back.active=!history.isEmpty();
            previous=addRenderableWidget(Button.builder(Component.literal("‹"),b->change(-1)).bounds(x+55,y+h-22,20,18).build());
            next=addRenderableWidget(Button.builder(Component.literal("›"),b->change(1)).bounds(x+w-25,y+h-22,20,18).build());
            List<Integer> heights=new ArrayList<>();List<List<net.minecraft.util.FormattedCharSequence>> notes=new ArrayList<>();
            for(var d:visible){var lines=lines(d,w-18);notes.add(lines);heights.add(Math.round(diagramHeight(d)*scale)+Math.min(3,lines.size())*10+8);}
            pages=browser_layout.pages(heights,bodyBottom-bodyTop,5);page=browser_layout.locate(pages,recipe);var range=pages.get(page);previous.active=page>0;next.active=page<pages.size()-1;
            List<Card> built=new ArrayList<>();int cy=bodyTop;int content=range.height();scroll=Math.max(0,Math.min(scroll,Math.max(0,content-(bodyBottom-bodyTop))));
            for(int i=range.first();i<range.end();i++){Display d=visible.get(i);int dh=diagramHeight(d),dw=diagramWidth(d);int plusX=dw-22,plusY=d.slots().stream().filter(s->s.output()&&!s.label().getString().equals(tr("returned").getString())).findFirst().map(DisplaySlot::y).orElse(30);
                Card c=new Card(d,i,x+8,cy-scroll,dh,heights.get(i),plusX,plusY,notes.get(i));built.add(c);
                int bx=c.ox()+Math.round(plusX*scale),by=c.oy()+Math.round(plusY*scale),bs=Math.max(8,Math.round(18*scale));
                // Only full, currently visible controls receive input; oversized tiny-window cards scroll.
                if(by>=bodyTop&&by+bs<=bodyBottom){Button fill=Button.builder(Component.literal("+"),b->transfer(d)).bounds(bx,by,bs,bs).build();fills.add(fill);addRenderableWidget(fill);}
                cy+=heights.get(i)+5;}
            cards=List.copyOf(built);updateFillButtons();
        }
        void updateFillButtons(){for(int i=0;i<fills.size();i++){var b=fills.get(i);Card c=cards.stream().filter(v->v.ox()+Math.round(v.plusX()*scale)==b.getX()&&v.oy()+Math.round(v.plusY()*scale)==b.getY()).findFirst().orElse(null);if(c!=null)b.active=!waiting&&reasonFor(c.display())==transfer_reason.OK;}}
        void scaleRecipe(int value){recipe_browser_preferences.recipeScale(value);rebuild();}
        void change(int direction){if(pages.size()<2)return;int target=Math.max(0,Math.min(pages.size()-1,page+direction));recipe=pages.get(target).first();scroll=0;rebuild();}
        void transfer(Display d){if(waiting)return;var root=container();if(root==null)return;var reason=reasonFor(d);if(reason!=transfer_reason.OK){notice=Component.translatable(reason.key());return;}waiting=true;waitTicks=0;request=++nextRequest;waitingRecipe=d.recipe().getId();recipe_unlocks.requestTransfer(root.getMenu().containerId,waitingRecipe,Screen.hasShiftDown(),recipe_browser_index.recipeFingerprint(waitingRecipe),request);updateFillButtons();}
        private void panel(GuiGraphics g,int xx,int yy,int ww,int hh){g.fill(xx,yy,xx+ww,yy+hh,0xFFC6C6C6);g.fill(xx,yy,xx+ww-1,yy+1,0xFFFFFFFF);g.fill(xx,yy,xx+1,yy+hh-1,0xFFFFFFFF);g.fill(xx+ww-1,yy,xx+ww,yy+hh,0xFF555555);g.fill(xx,yy+hh-1,xx+ww,yy+hh,0xFF555555);}
        private void slot(GuiGraphics g,int xx,int yy,boolean over){g.fill(xx,yy,xx+18,yy+18,over?0xFFAAAAAA:0xFF8B8B8B);g.fill(xx,yy,xx+18,yy+1,0xFF373737);g.fill(xx,yy,xx+1,yy+18,0xFF373737);g.fill(xx+17,yy,xx+18,yy+18,0xFFFFFFFF);g.fill(xx,yy+17,xx+18,yy+18,0xFFFFFFFF);}
        private void diagram(GuiGraphics g,Display d){int ax=82,ay=35;if(d.recipe() instanceof processing_recipe){ax=124;ay=27;}
            else if(d.recipe() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe){slot(g,24,58,false);g.fill(29,40,37,51,0xFFB68230);g.fill(31,36,35,48,0xFFFFD256);ax=76;ay=35;}
            else if(d.recipe() instanceof CraftingRecipe){int columns=d.recipe() instanceof net.minecraftforge.common.crafting.IShapedRecipe<?> shaped?Math.max(3,shaped.getRecipeWidth()):3,rows=d.recipe() instanceof net.minecraftforge.common.crafting.IShapedRecipe<?> shaped?Math.max(3,shaped.getRecipeHeight()):3;ax=4+columns*20+18;for(int r=0;r<rows;r++)for(int col=0;col<columns;col++)slot(g,4+col*20,10+r*20,false);}
            else if(d.recipe() instanceof net.minecraft.world.item.crafting.SmithingRecipe){ax=99;ay=32;}else if(!(d.recipe() instanceof net.minecraft.world.item.crafting.StonecutterRecipe))return;
            g.fill(ax,ay+5,ax+22,ay+8,0xFF858585);for(int i=0;i<5;i++)g.fill(ax+18+i,ay+1+i,ax+19+i,ay+12-i,0xFF858585);}
        void hover(double mx,double my){hovered=null;hoveredStack=ItemStack.EMPTY;if(!inside(mx,my,x+4,bodyTop,w-8,bodyBottom-bodyTop))return;for(var c:cards)for(var s:c.display().slots())if(browser_layout.hit(mx,my,c.ox(),c.oy(),scale,s.x(),s.y(),18,18)){hovered=s;hoveredStack=s.stack(cycle);recipe=c.index();return;}}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){if(lastSource!=recipe_browser_index.recipes()||revision!=recipe_browser_index.revision())refresh();renderBackground(g);panel(g,x,y,w,h);text(g,getTitle(),x+7,y+7,w-34,INK);if(!Screen.hasShiftDown())cycle=System.currentTimeMillis()/1000;hover(mx,my);
            g.enableScissor(x+3,bodyTop,x+w-3,bodyBottom);
            for(Card c:cards){panel(g,x+4,c.oy(),w-8,c.height());g.pose().pushPose();g.pose().translate(c.ox(),c.oy(),0);g.pose().scale(scale,scale,1);diagram(g,c.display());
                for(var s:c.display().slots()){slot(g,s.x(),s.y(),hovered==s);ItemStack stack=s.stack(cycle);g.renderItem(stack,s.x()+1,s.y()+1);g.renderItemDecorations(font,stack,s.x()+1,s.y()+1);}
                g.pose().popPose();int yy=c.oy()+Math.round(c.diagramHeight()*scale);for(int line=0;line<Math.min(3,c.lines().size());line++){g.drawString(font,c.lines().get(line),x+8,yy+line*10,INK,false);}if(c.lines().size()>3)g.drawString(font,"…",x+w-17,yy+20,MUTED,false);
            }g.disableScissor();if(visible.isEmpty())g.drawWordWrap(font,tr("empty_recipes"),x+8,bodyTop+8,w-16,INK);
            text(g,notice,x+6,y+h-36,w-12,MUTED);super.render(g,mx,my,partial);g.drawCenteredString(font,Component.literal((page+1)+" / "+pages.size()),x+(w+38)/2,y+h-16,INK);
            if(hovered!=null&&!hoveredStack.isEmpty()){Component label=hovered.label().copy().append(" ×"+hovered.count());if(hovered.ingredient().getItems().length>1)label=label.copy().append(tr("alternatives",hovered.ingredient().getItems().length));itemTooltip(g,hoveredStack,label,mx,my);}
            else {boolean hint=false;for(var b:fills)if(b.isMouseOver(mx,my)){Card c=cards.stream().filter(v->v.ox()+Math.round(v.plusX()*scale)==b.getX()&&v.oy()+Math.round(v.plusY()*scale)==b.getY()).findFirst().orElse(null);if(c!=null){var reason=reasonFor(c.display());g.renderTooltip(font,waiting?tr("waiting"):reason==transfer_reason.OK?tr("transfer_hint"):Component.translatable(reason.key()),mx,my);hint=true;}break;}
                if(!hint)for(var c:cards)if(inside(mx,my,x+5,c.oy()+Math.round(c.diagramHeight()*scale),w-10,Math.min(3,c.lines().size())*10)){List<Component> notes=new ArrayList<>(c.display().notes());notes.add(Component.literal(c.display().recipe().getId().toString()));g.renderTooltip(font,notes.stream().map(Component::getVisualOrderText).toList(),mx,my);break;}
            }
        }
        @Override public boolean mouseClicked(double mx,double my,int button){if(button==3&&!history.isEmpty()){back();return true;}hover(mx,my);if(hovered!=null&&!hoveredStack.isEmpty()){if(button==2){recipe_browser_preferences.toggle(hoveredStack);return true;}if(button==0||button==1){navigate(hoveredStack,button==1,false);return true;}}super.mouseClicked(mx,my,button);return true;}
        @Override public boolean mouseScrolled(double mx,double my,double amount){if(Screen.hasAltDown()&&recipe_browser_tooltips.scroll(amount))return true;if(inside(mx,my,x,bodyTop,w,bodyBottom-bodyTop)&&pages.get(page).height()>bodyBottom-bodyTop){scroll+=(int)(-amount*16);rebuild();return true;}if(inside(mx,my,x,y,w,h)&&amount!=0)change(amount>0?-1:1);return true;}
        boolean shortcut(recipe_browser_key_bridge.Action action){switch(action){case BACK->{if(history.isEmpty())return false;back();return true;}case PREVIOUS,NEXT->{change(action==recipe_browser_key_bridge.Action.NEXT?1:-1);return true;}case FAVORITE->{ItemStack item=!hoveredStack.isEmpty()?hoveredStack:subject;if(item.isEmpty())return false;recipe_browser_preferences.toggle(item);return true;}case RECIPES,USES->{if(hoveredStack.isEmpty())return false;navigate(hoveredStack,action==recipe_browser_key_bridge.Action.USES,false);return true;}default->{return false;}}}
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
        @Override public void tick(){if(!live(this)){Minecraft.getInstance().setScreen(null);return;}var reply=browser_transfer_feedback.last();if(waiting&&reply!=null&&reply.request()==request&&reply.menu()==container().getMenu().containerId&&reply.recipe().equals(waitingRecipe)){waiting=false;notice=Component.translatable(reply.reason().key());updateFillButtons();}if(waiting&&++waitTicks>80){waiting=false;notice=tr("timeout");updateFillButtons();}if(Minecraft.getInstance().player.tickCount%5==0)updateFillButtons();}
        @Override public boolean isPauseScreen(){return false;}
    }
'''+s[end:]
p.write_text(s,encoding='utf-8')
