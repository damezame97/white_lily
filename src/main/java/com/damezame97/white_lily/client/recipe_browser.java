package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.recipe_unlock_client;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import com.damezame97.white_lily.recipebook.browser_order;
import com.damezame97.white_lily.recipebook.browser_history;
import com.damezame97.white_lily.recipebook.browser_card_layout;
import com.damezame97.white_lily.recipebook.browser_transfer_state;
import com.damezame97.white_lily.recipebook.browser_layout;
import com.damezame97.white_lily.recipebook.browser_display.*;
import com.damezame97.white_lily.recipebook.browser_transfer;
import com.damezame97.white_lily.recipebook.browser_transfer_feedback;
import com.damezame97.white_lily.recipebook.transfer_reason;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraft.client.gui.components.Tooltip;
import com.damezame97.white_lily.processing.processing_menu;
import com.damezame97.white_lily.processing.processing_recipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The browser never requests creative slots: filling is delegated to the server's normal recipe placement. */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT)
public final class recipe_browser
{
    private static final String KEY = recipe_browser_index.KEY;
    private static final int PAPER = 0xF01E2531, EDGE = 0xFF66788E, TEXT = 0xFFE7E2D6, GOLD = 0xFFFFD578;
    @Nullable private static Panel panel;
    private static String search = "";
    private static boolean favoritesOnly;
    private static boolean collapsed;
    private static boolean openingChild;
    private static boolean renderedBeforeTooltip;
    private static int listPage;
    private static final browser_history HISTORY = new browser_history();
    @Nullable private static browser_history.Query lastDetails;
    private static final java.util.Set<com.mojang.blaze3d.platform.InputConstants.Key> GUI_DOWN=new java.util.HashSet<>();
    static { recipe_browser_key_bridge.install(recipe_browser::handleShortcut); }
    private static boolean inputFocused(Screen screen)
    {return screen.getFocused() instanceof EditBox edit&&edit.isFocused() || screen instanceof BrowserScreen browser&&browser.contents!=null&&browser.contents.query.isFocused() || panel!=null&&panel.parent==screen&&panel.query.isFocused();}
    private static boolean dispatch(com.mojang.blaze3d.platform.InputConstants.Key key)
    {
        var mc=Minecraft.getInstance();if(mc.screen==null||inputFocused(mc.screen))return false;
        for(int i=0;i<8;i++)if(white_lily_keybinds.ALL.get(i).isActiveAndMatches(key))
        {if(GUI_DOWN.contains(key))return true;boolean handled=handleShortcut(recipe_browser_key_bridge.Action.values()[i]);if(handled)GUI_DOWN.add(key);return handled;}
        return false;
    }
    private static boolean handleShortcut(recipe_browser_key_bridge.Action action)
    {
        var screen=Minecraft.getInstance().screen;if(screen==null||inputFocused(screen))return false;
        if(screen instanceof RecipeScreen details)return details.shortcut(action);
        if(screen instanceof BrowserScreen browser)return browser.contents.shortcut(action);
        Panel p=panel(screen);return p!=null&&p.shortcut(action);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void keyRelease(ScreenEvent.KeyReleased.Pre event)
    {if(GUI_DOWN.remove(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(),event.getScanCode())))event.setCanceled(true);}
    @SubscribeEvent public static void beforeFrame(ScreenEvent.Render.Pre event){renderedBeforeTooltip=false;recipe_browser_tooltips.frame();}
    /** Called at the container tooltip boundary, after slots but before their native tooltip. */
    public static void beforeContainerTooltip(AbstractContainerScreen<?> screen,GuiGraphics g,int mx,int my)
    {crafting_choice_client.renderButton(screen,g,mx,my);Panel p=panel(screen);if(p!=null&&!renderedBeforeTooltip){renderedBeforeTooltip=true;p.renderBase(g,mx,my,0);}}
    /** A foreground popup owns the mouse: do not let a native slot tooltip cover it. */
    public static boolean blocksContainerTooltip(AbstractContainerScreen<?> screen,int mx,int my)
    {Panel p=panel(screen);return p!=null&&p.blocksContainerTooltip(mx,my);}
    @SubscribeEvent public static void tooltipWidth(RenderTooltipEvent.GatherComponents event)
    {
        var screen=Minecraft.getInstance().screen;
        if(screen instanceof AbstractContainerScreen<?> || screen instanceof RecipeScreen || screen instanceof BrowserScreen||crafting_choice_client.isModal(screen))
            event.setMaxWidth(Math.max(60,Math.min(300,event.getScreenWidth()-20)));
    }

    /** Used by the client mixin to preserve the underlying inventory while inspecting a recipe. */
    public static boolean isOpeningChild() { return openingChild; }
    private static void show(Screen next)
    {
        openingChild = true;
        try { Minecraft.getInstance().setScreen(next); }
        finally { openingChild = false; }
    }
    public static void openModal(Screen screen){show(screen);}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event)
    {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container) {
            ItemStack anchor=panel!=null&&panel.parent==container?panel.firstItem():ItemStack.EMPTY;
            panel = new Panel(container, false, false);panel.locateItem(anchor);
        }
    }

    private static Panel panel(Screen screen)
    {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return null;
        if (panel == null || panel.parent != container) panel = new Panel(container, false, false);
        return panel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event)
    {
        Panel current = panel(event.getScreen());
        if (current == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        if(!renderedBeforeTooltip){renderedBeforeTooltip=true;current.renderBase(graphics,event.getMouseX(),event.getMouseY(),event.getPartialTick());}
        current.renderForeground(graphics,event.getMouseX(),event.getMouseY());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event)
    {
        if(crafting_choice_client.click(event.getScreen(),event.getMouseX(),event.getMouseY(),event.getButton())){event.setCanceled(true);return;}
        if(dispatch(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.getButton()))){event.setCanceled(true);return;}
        Panel current = panel(event.getScreen());
        if (current != null && current.click(event.getMouseX(), event.getMouseY(), event.getButton())) {current.capturedButton=event.getButton();event.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void release(ScreenEvent.MouseButtonReleased.Pre event)
    {if(crafting_choice_client.consumeRelease()){event.setCanceled(true);return;}if(GUI_DOWN.remove(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(event.getButton()))){event.setCanceled(true);return;}Panel p=panel(event.getScreen());if(p!=null&&p.capturedButton==event.getButton()){p.capturedButton=-1;event.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void drag(ScreenEvent.MouseDragged.Pre event)
    {Panel p=panel(event.getScreen());if(p!=null&&p.capturedButton==event.getMouseButton()){p.query.mouseDragged(event.getMouseX(),event.getMouseY(),event.getMouseButton(),event.getDragX(),event.getDragY());event.setCanceled(true);}}
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event)
    {
        if(Screen.hasAltDown()&&recipe_browser_tooltips.scroll(event.getScrollDelta())){event.setCanceled(true);return;}
        Panel current = panel(event.getScreen());
        if (current != null && current.scroll(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event)
    {
        if(dispatch(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKeyCode(),event.getScanCode()))){event.setCanceled(true);return;}
        Panel current = panel(event.getScreen());
        if (current == null) return;
        int key = event.getKeyCode();
        if (current.closePopupOnEscape(key)) { event.setCanceled(true); return; }
        if (current.query.isFocused())
        {
            if (key == GLFW.GLFW_KEY_ESCAPE) current.query.setFocused(false);
            else current.query.keyPressed(key, event.getScanCode(), event.getModifiers());
            event.setCanceled(true); return;
        }
        if (event.getScreen().getFocused() instanceof EditBox edit && edit.isFocused()) return;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void character(ScreenEvent.CharacterTyped.Pre event)
    {
        Panel current = panel(event.getScreen());
        if (current != null && current.query.isFocused())
        { current.query.charTyped(event.getCodePoint(), event.getModifiers()); event.setCanceled(true); }
    }
    @SubscribeEvent public static void recipes(RecipesUpdatedEvent event) { recipe_browser_index.invalidate(); }
    @SubscribeEvent public static void tags(TagsUpdatedEvent event) { recipe_browser_index.invalidate(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event)
    { panel = null; processing_client_context.clear(); browser_cheat_client.reset(); recipe_browser_index.clear(); recipe_unlock_client.clear(); search = "";listPage=0;GUI_DOWN.clear();browser_transfer_feedback.clear();HISTORY.clear();lastDetails=null; }

    private static void open(Screen parent, ItemStack stack, boolean uses)
    { if(parent instanceof RecipeScreen current)current.navigate(stack,uses,false);else {rememberBeforeOpen(stack,uses,false);show(new RecipeScreen(parent, stack.copy(), uses, false));} }
    private static void all(Screen parent)
    { rememberBeforeOpen(ItemStack.EMPTY,false,true);show(new RecipeScreen(parent, ItemStack.EMPTY, false, true)); }
    private static void rememberBeforeOpen(ItemStack target,boolean uses,boolean all)
    { if(lastDetails!=null)HISTORY.push(lastDetails,target,uses,all); }

    @Nullable private static AbstractContainerScreen<?> rootContainer(Screen screen)
    {
        if (screen instanceof RecipeScreen details) return rootContainer(details.parent);
        if (screen instanceof BrowserScreen browser) return rootContainer(browser.parent);
        return screen instanceof AbstractContainerScreen<?> container ? container : null;
    }
    private static boolean live(Screen screen)
    {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> root = rootContainer(screen);
        boolean valid=root!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&mc.level!=null&&mc.player.containerMenu==root.getMenu()&&root.getMenu().stillValid(mc.player);
        if(!valid&&root!=null&&root.getMenu() instanceof processing_menu)processing_client_context.clear(root.getMenu().containerId);
        return valid;
    }

    private static void box(GuiGraphics g, int x, int y, int width, int height)
    { g.fill(x, y, x + width, y + height, PAPER); g.renderOutline(x, y, width, height, EDGE); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h)
    { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private static Component tr(String key, Object... arguments) { return Component.translatable(KEY + key, arguments); }
    private static void text(GuiGraphics g, Component component, int x, int y, int maxWidth, int color)
    {
        var font = Minecraft.getInstance().font;
        String value = font.plainSubstrByWidth(component.getString(), Math.max(0, maxWidth));
        g.drawString(font, value, x, y, color, false);
    }
    public static boolean tooltipSurface(Screen screen){return screen instanceof AbstractContainerScreen<?>||screen instanceof RecipeScreen||screen instanceof BrowserScreen||crafting_choice_client.isModal(screen);}
    private static void itemTooltip(GuiGraphics g, ItemStack item, Component label, int mx, int my, boolean listItem)
    {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, item));
        if (label != null) lines.add(label);
        lines.add(tr("item_mod",recipe_browser_index.modName(item)));
        if (listItem && browser_cheat_client.itemGive())
        {
            if (browser_cheat_client.favoriteConflictsWithTake())
                lines.add(tr("item_take_controls_conflict"));
            else
                lines.add(white_lily_key_display.hint(KEY+"item_take_controls",
                        white_lily_keybinds.BROWSER_FAVORITE));
        }
        else
            lines.add(white_lily_key_display.hint(KEY+"item_controls.keys",
                    white_lily_keybinds.BROWSER_RECIPES, white_lily_keybinds.BROWSER_USES,
                    white_lily_keybinds.BROWSER_FAVORITE));
        g.renderTooltip(mc.font, lines, item.getTooltipImage(), item, mx, my);
    }

    private static final class Panel
    {
        final Screen parent;
        final boolean standalone;
        final boolean overlay;
        final EditBox query;
        // Item models render at z=150, ordinary tooltips at z=400. The popup surface and
        // its own tooltip are pushed above both as one active layer, then popped.
        static final int HEADER_H=21,GRID_TOP=41,BOTTOM_RESERVE=36,CHEAT_H=16,CONTROL_H=16,SCALE_GROUP_W=60,COMPACT_SCALE_GROUP_W=48,COMPACT_CHEAT_W=12,MIN_SIDEBAR_WIDTH=68,NORMAL_CONTROLS_WIDTH=130,POPUP_Z=450;
        int x, y, w, h, columns, rows, page;
        int capturedButton=-1;
        int lastColumns=-1,lastRows=-1,cellSize=18,lastParentWidth=-1,lastParentHeight=-1;
        List<List<GridCell>> grid=List.of(List.of());
        browser_order.Mode sort=recipe_browser_preferences.sort();
        record GridCell(int cell,ItemStack stack,String header) { }
        boolean compact, tabVisible, spaceShort, compactControls;
        boolean cheatOpen,settingsOpen;
        long revision = Long.MIN_VALUE;
        List<ItemStack> source = List.of(), filtered = List.of();
        ItemStack hovered = ItemStack.EMPTY;

        Panel(Screen parent, boolean standalone, boolean overlay)
        {
            this.parent = parent; this.standalone = standalone;this.overlay=overlay;this.page=listPage;
            query = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 16, tr("search"));
            query.setBordered(false);query.setMaxLength(160); query.setHint(tr("search_hint")); query.setValue(search);
            query.setResponder(value -> { search = value; page = 0; refreshFilter(); });
            int restoredPage=listPage;layout();page=restoredPage;refreshFilter();
            if(!browser_cheat_client.allowed()) browser_cheat_client.requestPermission();
        }

        void layout()
        {
            int parentWidth=parent.width,parentHeight=parent.height;
            if(lastParentWidth>=0&&(lastParentWidth!=parentWidth||lastParentHeight!=parentHeight))
            {cheatOpen=false;settingsOpen=false;}
            lastParentWidth=parentWidth;lastParentHeight=parentHeight;
            cellSize=browser_layout.cell(recipe_browser_preferences.itemScale());
            int minHeight=GRID_TOP+cellSize+BOTTOM_RESERVE;
            compactControls=false;
            if (standalone)
            {
                if(overlay)
                {
                    x=4;y=4;w=Math.min(360,Math.max(40,parentWidth-24));w=Math.min(w,Math.max(40,parentWidth-8));
                    h=Math.min(parentHeight-8,Math.max(minHeight,parentHeight-24));
                    x=Math.max(4,(parentWidth-w)/2);y=Math.max(4,(parentHeight-h)/2);
                }
                else
                { x = 8; y = 26; w = Math.max(40, parent.width - 16); h = Math.max(60, parent.height - 54); }
                compact = false; spaceShort = false; tabVisible = true;
                compactControls = w < NORMAL_CONTROLS_WIDTH;
            }
            else
            {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) parent;
                int right = screen.getGuiLeft() + screen.getXSize();
                int available = parent.width - right - 4;
                int panelHeight = Math.max(40, parent.height - 8);
                spaceShort = available < MIN_SIDEBAR_WIDTH || panelHeight < minHeight;
                compact = collapsed || spaceShort;
                x = right + 2; y = 4; w = Math.max(MIN_SIDEBAR_WIDTH, available); h = panelHeight;
                tabVisible = true;
                if (compact)
                {
                    w = 18; h = 18; y = Math.max(2, screen.getGuiTop());
                    if (available < 18)
                    {
                        x = parent.width - 20;
                        if (screen.getGuiTop() >= 20) y = screen.getGuiTop() - 19;
                        else if (parent.height - screen.getGuiTop() - screen.getYSize() >= 20) y = screen.getGuiTop() + screen.getYSize() + 1;
                        else {
                            tabVisible=false;
                            for(int candidate=2;candidate+18<=parent.width;candidate+=20){final int tx=candidate,ty=2;
                                boolean slot=screen.getMenu().slots.stream().anyMatch(s->tx<screen.getGuiLeft()+s.x+18&&tx+18>screen.getGuiLeft()+s.x&&ty<screen.getGuiTop()+s.y+18&&ty+18>screen.getGuiTop()+s.y);
                                if(!slot){x=tx;y=ty;tabVisible=true;break;}}
                        }
                    }
                }
                else compactControls = w < NORMAL_CONTROLS_WIDTH;
            }
            if(compact){cheatOpen=false;settingsOpen=false;compactControls=false;}
            columns = browser_layout.columns(w,cellSize); rows = browser_layout.itemRows(h,GRID_TOP,BOTTOM_RESERVE,cellSize);
            query.setX(x + 4); query.setY(y + 22); query.setWidth(Math.max(10, w - 8));
            if (compact) query.setFocused(false);
            if(lastColumns!=columns||lastRows!=rows){ItemStack anchor=firstItem();lastColumns=columns;lastRows=rows;rebuildGrid();locateItem(anchor);}
        }
        ItemStack firstItem(){return page>=0&&page<grid.size()?grid.get(page).stream().map(GridCell::stack).filter(stack->!stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY):ItemStack.EMPTY;}
        void locateItem(ItemStack anchor){if(anchor.isEmpty())return;for(int i=0;i<grid.size();i++)for(var cell:grid.get(i))if(ItemStack.isSameItemSameTags(anchor,cell.stack())){page=i;listPage=page;return;}}
        void scaleItems(int value){ItemStack anchor=firstItem();recipe_browser_preferences.itemScale(value);lastColumns=-1;layout();locateItem(anchor);}
        void refreshFilter()
        {
            ItemStack anchor=firstItem();
            source = recipe_browser_index.sortedItems(sort); revision = recipe_browser_index.revision();
            filtered = source.stream().filter(stack -> !favoritesOnly || recipe_browser_preferences.contains(stack))
                    .filter(stack -> recipe_browser_index.matches(stack, query.getValue())).toList();
            rebuildGrid();
            locateItem(anchor);
        }
        void rebuildGrid()
        {
            grid=browser_order.grid(filtered,columns,rows,cellSize,sort==browser_order.Mode.MOD,
                    item->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace(),recipe_browser_index::modName)
                    .stream().map(cells->cells.stream().map(cell->new GridCell(cell.position(),cell.item()==null?ItemStack.EMPTY:cell.item(),cell.header())).toList()).toList();
            page=Math.max(0,Math.min(page,grid.size()-1));listPage=page;
        }
        int pages() { return grid.size(); }
        void changePage(int direction) { page = Math.floorMod(page + direction, pages());listPage=page; }
        ItemStack hovered() { return hovered; }
        boolean shortcut(recipe_browser_key_bridge.Action action)
        {
            if(query.isFocused()||popupOpen())return false;
            switch(action){
                case TOGGLE -> {
                    if(compact)
                    {
                        collapsed=false;layout();
                        if(compact)show(new BrowserScreen(parent,true));
                    }
                    else {collapsed=true;layout();}
                    return true;
                }
                case SEARCH -> {
                    if(compact)
                    {
                        collapsed=false;layout();
                        if(compact)show(new BrowserScreen(parent,true));else query.setFocused(true);
                    }
                    else query.setFocused(true);
                    return true;
                }
                case RECIPES,USES,FAVORITE -> {
                    ItemStack stack=hovered();if(stack.isEmpty()&&parent instanceof AbstractContainerScreen<?> c&&c.getSlotUnderMouse()!=null)stack=c.getSlotUnderMouse().getItem();if(stack.isEmpty())return false;
                    if(action==recipe_browser_key_bridge.Action.FAVORITE){
                        if(browser_cheat_client.favoriteConflictsWithTake())return false;
                        recipe_browser_preferences.toggle(stack);refreshFilter();
                    }else open(parent,stack,action==recipe_browser_key_bridge.Action.USES);return true;
                }
                default -> {return false;}
            }
        }


        int controlY() { return y + h - 35; }
        int scaleGroupW() { return compactControls?COMPACT_SCALE_GROUP_W:SCALE_GROUP_W; }
        int scaleX() { return x + 4; }
        int scaleMinusX() { return scaleX(); }
        int scaleMinusW() { return compactControls?10:14; }
        int scalePercentX() { return compactControls?scaleX()+16:scaleX()+20; }
        int scalePercentW() { return compactControls?24:26; }
        int scalePlusX() { return compactControls?scaleX()+42:scaleX()+48; }
        int scalePlusW() { return compactControls?10:14; }
        int cheatW() { return compactControls?COMPACT_CHEAT_W:Math.min(58, Math.max(18, w - 8)); }
        int cheatX() { return x + w - cheatW() - 4; }
        int cheatY() { return controlY(); }
        browser_layout.Popup cheatPopup(){return browser_layout.placePopup(cheatX(),cheatY(),cheatW(),CHEAT_H,Math.min(180,Math.max(120,w-8)),80,parent.width,parent.height,true);}
        browser_layout.Popup settingsPopup(){return browser_layout.placePopup(x+4,y+3,Math.max(1,w-8),18,Math.min(176,Math.max(120,w-8)),88,parent.width,parent.height,false);}
        boolean popupOpen(){return cheatOpen||settingsOpen;}
        /** While a menu is open, no native slot tooltip may compete with its foreground layer. */
        boolean blocksContainerTooltip(double mx,double my){return popupOpen();}
        boolean closePopupOnEscape(int key){if(key==GLFW.GLFW_KEY_ESCAPE&&popupOpen()){cheatOpen=false;settingsOpen=false;return true;}return false;}

        void drawScaleControls(GuiGraphics g,int mx,int my)
        {
            int cy=controlY();
            if(!popupOpen())
            {
                if(inside(mx,my,scaleMinusX(),cy,scaleMinusW(),CONTROL_H))g.fill(scaleMinusX(),cy,scaleMinusX()+scaleMinusW(),cy+CONTROL_H,0x30FFFFFF);
                if(inside(mx,my,scalePercentX(),cy,scalePercentW(),CONTROL_H))g.fill(scalePercentX(),cy,scalePercentX()+scalePercentW(),cy+CONTROL_H,0x30FFFFFF);
                if(inside(mx,my,scalePlusX(),cy,scalePlusW(),CONTROL_H))g.fill(scalePlusX(),cy,scalePlusX()+scalePlusW(),cy+CONTROL_H,0x30FFFFFF);
            }
            g.drawString(Minecraft.getInstance().font,"−",scaleMinusX()+4,cy+4,TEXT,false);
            text(g,Component.literal(recipe_browser_preferences.itemScale()+"%"),scalePercentX()+1,cy+4,scalePercentW(),GOLD);
            g.drawString(Minecraft.getInstance().font,"+",scalePlusX()+2,cy+4,TEXT,false);
        }

        void drawCheatButton(GuiGraphics g)
        {
            String label = browser_cheat_client.itemGive() ? "cheat_button_take"
                    : browser_cheat_client.showAll() ? "cheat_button_all" : "cheat_button";
            int bx = cheatX(), by = cheatY(), bw = cheatW(), bh = CHEAT_H;
            g.fill(bx, by, bx + bw, by + bh, browser_cheat_client.itemGive() ? 0xA05A2020 : 0xA01E2531);
            g.renderOutline(bx, by, bw, bh, browser_cheat_client.allowed() ? EDGE : 0xFF555555);
            if(compactControls)
                text(g,Component.literal("!"),bx+4,by+4,8,browser_cheat_client.allowed()?(browser_cheat_client.itemGive()?0xFFFF8888:browser_cheat_client.showAll()?GOLD:TEXT):0xFF888888);
            else
                text(g, tr(label), bx + 3, by + 4, bw - 6, browser_cheat_client.allowed() ? TEXT : 0xFF888888);
        }

        void drawMenuBackground(GuiGraphics g,int px,int py,int pw,int ph)
        {
            g.fill(px-1,py-1,px+pw+1,py+ph+1,0x70000000);
            g.fill(px,py,px+pw,py+ph,0xFF1E2531);
            g.renderOutline(px,py,pw,ph,EDGE);
        }

        void drawCheatPanel(GuiGraphics g,int mx,int my)
        {
            if (cheatOpen && !browser_cheat_client.allowed()) cheatOpen = false;
            if (!cheatOpen) return;
            var popup=cheatPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            g.pose().pushPose();
            g.pose().translate(0,0,POPUP_Z);
            try
            {
                drawMenuBackground(g,px,py,pw,ph);
                text(g, tr("cheat_title"), px + 4, py + 4, pw - 22, GOLD);
                g.drawString(Minecraft.getInstance().font,"×",px+pw-13,py+4,TEXT,false);
                if (inside(mx,my,px+2,py+19,pw-4,16)) g.fill(px+2,py+19,px+pw-2,py+35,0x30FFFFFF);
                if (inside(mx,my,px+2,py+37,pw-4,16)) g.fill(px+2,py+37,px+pw-2,py+53,0x30FFFFFF);
                if (!browser_cheat_client.allowed())
                {
                    text(g, tr("cheat_no_permission"), px + 4, py + 24, pw - 8, 0xFFFF8888);
                    return;
                }
                text(g, tr("cheat_take", tr(browser_cheat_client.itemGive() ? "on" : "off")), px + 4, py + 23, pw - 8,
                        browser_cheat_client.itemGive() ? 0xFFFF8888 : TEXT);
                text(g, tr("cheat_show_all", tr(browser_cheat_client.showAll() ? "on" : "off")), px + 4, py + 41, pw - 8,
                        browser_cheat_client.showAll() ? GOLD : TEXT);
                text(g, tr("cheat_close"), px + 4, py + ph - 13, pw - 20, EDGE);
            }
            finally {g.pose().popPose();}
        }

        boolean clickCheat(double mx, double my, int button)
        {
            if (cheatOpen && !browser_cheat_client.allowed()) cheatOpen = false;
            if (!cheatOpen)
            {
                if (inside(mx,my,cheatX(),cheatY(),cheatW(),CHEAT_H))
                {
                    if (browser_cheat_client.allowed()) { cheatOpen=true; settingsOpen=false; query.setFocused(false); }
                    else browser_cheat_client.requestPermission();
                    return true;
                }
                return false;
            }
            var popup=cheatPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            if (!popup.contains(mx,my)) { cheatOpen=false; return true; }
            if (button!=0) return true;
            if (inside(mx,my,px+pw-16,py+2,14,14)) { cheatOpen=false; return true; }
            if (!browser_cheat_client.allowed()) return true;
            if (inside(mx,my,px+2,py+19,pw-4,16)) { browser_cheat_client.toggleItemGive(); return true; }
            if (inside(mx,my,px+2,py+37,pw-4,16)) { browser_cheat_client.toggleShowAll(); return true; }
            return true;
        }

        void drawSettingsPanel(GuiGraphics g,int mx,int my)
        {
            if(!settingsOpen)return;
            var popup=settingsPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            g.pose().pushPose();
            g.pose().translate(0,0,POPUP_Z);
            try
            {
                drawMenuBackground(g,px,py,pw,ph);
                text(g,tr("settings"),px+4,py+4,pw-22,GOLD);
                g.drawString(Minecraft.getInstance().font,"×",px+pw-13,py+4,TEXT,false);
                if(inside(mx,my,px+2,py+19,pw-4,16))g.fill(px+2,py+19,px+pw-2,py+35,0x30FFFFFF);
                if(inside(mx,my,px+2,py+37,pw-4,16))g.fill(px+2,py+37,px+pw-2,py+53,0x30FFFFFF);
                if(inside(mx,my,px+2,py+55,pw-4,18))g.fill(px+2,py+55,px+pw-2,py+73,0x30FFFFFF);
                text(g,tr("sort."+sort.name().toLowerCase(java.util.Locale.ROOT)),px+4,py+23,pw-8,TEXT);
                text(g,tr("favorite_filter",tr(favoritesOnly?"on":"off")),px+4,py+41,pw-8,favoritesOnly?GOLD:TEXT);
                g.drawString(Minecraft.getInstance().font,"−",px+5,py+59,TEXT,false);
                text(g,Component.literal(recipe_browser_preferences.itemScale()+"%"),px+22,py+59,Math.max(20,pw-44),GOLD);
                g.drawString(Minecraft.getInstance().font,"+",px+pw-13,py+59,TEXT,false);
                text(g,tr("settings_reset"),px+4,py+ph-12,pw-8,EDGE);
            }
            finally {g.pose().popPose();}
        }

        boolean clickSettings(double mx,double my,int button)
        {
            if(!settingsOpen)return false;
            var popup=settingsPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            if(!popup.contains(mx,my)){settingsOpen=false;return true;}
            if(button!=0)return true;
            if(inside(mx,my,px+pw-16,py+2,14,14)){settingsOpen=false;return true;}
            if(inside(mx,my,px+2,py+19,pw-4,16))
            {recipe_browser_preferences.nextSort();sort=recipe_browser_preferences.sort();refreshFilter();return true;}
            if(inside(mx,my,px+2,py+37,pw-4,16))
            {favoritesOnly=!favoritesOnly;page=0;refreshFilter();return true;}
            if(inside(mx,my,px+2,py+55,pw-4,18))
            {
                if(mx<px+22)scaleItems(recipe_browser_preferences.itemScale()-10);
                else if(mx>=px+pw-22)scaleItems(recipe_browser_preferences.itemScale()+10);
                else scaleItems(100);
                return true;
            }
            if(inside(mx,my,px+4,py+ph-14,pw-8,12)){scaleItems(100);return true;}
            return true;
        }

        void renderBase(GuiGraphics g, int mx, int my, float partial)
        {
            layout();
            if (source != recipe_browser_index.sortedItems(sort) || revision != recipe_browser_index.revision()) refreshFilter();
            hovered = ItemStack.EMPTY;
            if (compact)
            {
                if (!tabVisible) return;
                box(g, x, y, 18, 18); g.drawCenteredString(Minecraft.getInstance().font, "≡", x + 9, y + 5,
                        browser_cheat_client.itemGive() ? 0xFFFF5555 : browser_cheat_client.showAll() ? GOLD : TEXT);
                return;
            }
            // Only the two-row header and the two bottom rows keep a translucent backing.
            g.fill(x,y,x+w,y+GRID_TOP,0xA01E2531);g.fill(x,y+h-BOTTOM_RESERVE,x+w,y+h,0xA01E2531);
            g.drawString(Minecraft.getInstance().font, "≡", x + 5, y + 7, TEXT, false);
            Component panelTitle=parent instanceof BrowserScreen browser&&browser.overlay?tr("temporary_sidebar"):tr("title");
            if(!compactControls||w>=110)text(g, panelTitle, x + 17, y + 7, Math.max(4,w - 56), favoritesOnly?GOLD:TEXT);
            g.drawString(Minecraft.getInstance().font, "⚙", x + w - 31, y + 7, settingsOpen ? GOLD : TEXT, false);
            g.drawString(Minecraft.getInstance().font, standalone ? (overlay?"×":"?") : "−", x + w - 13, y + 7, TEXT, false);
            query.render(g, mx, my, partial);
            for(GridCell cell:grid.get(page))
            {
                int sx=x+4+(cell.cell()%columns)*cellSize,sy=y+GRID_TOP+(cell.cell()/columns)*cellSize;
                if(cell.stack().isEmpty()){text(g,Component.literal(cell.header()),x+4,sy+2,w-8,GOLD);continue;}
                ItemStack stack=cell.stack();
                if(!popupOpen()&&inside(mx,my,sx,sy,cellSize,cellSize)){hovered=stack;g.fill(sx,sy,sx+cellSize,sy+cellSize,0x805B6674);}
                g.pose().pushPose();g.pose().translate(sx,sy,0);float iconScale=cellSize/18F;g.pose().scale(iconScale,iconScale,1);g.renderItem(stack,1,1);g.renderItemDecorations(Minecraft.getInstance().font,stack,1,1);g.pose().popPose();
                if(recipe_browser_preferences.contains(stack))g.fill(sx+cellSize-5,sy+1,sx+cellSize-1,sy+3,GOLD);
            }
            int footer = y + h - 15;
            g.drawString(Minecraft.getInstance().font, "‹", x + 5, footer + 2, TEXT, false);
            text(g, Component.literal((page + 1) + "/" + pages()), x + 16, footer + 2, w - 32, TEXT);
            g.drawString(Minecraft.getInstance().font, "›", x + w - 10, footer + 2, TEXT, false);
            if (filtered.isEmpty()) text(g, tr("empty"), x + 5, y + GRID_TOP+2, w - 10, EDGE);
            drawScaleControls(g,mx,my);
            drawCheatButton(g);
        }

        void renderForeground(GuiGraphics g, int mx, int my)
        {
            if(compact)
            {
                if(tabVisible&&inside(mx,my,x,y,18,18))
                    g.renderTooltip(Minecraft.getInstance().font,tr(spaceShort?"sidebar_page":"sidebar_restore"),mx,my);
                return;
            }
            if(cheatOpen)
            {
                drawCheatPanel(g,mx,my);
                g.pose().pushPose();
                g.pose().translate(0,0,100);
                try { cheatTooltip(g,mx,my); }
                finally { g.pose().popPose(); }
                return;
            }
            if(settingsOpen)
            {
                drawSettingsPanel(g,mx,my);
                g.pose().pushPose();
                g.pose().translate(0,0,100);
                try { settingsTooltip(g,mx,my); }
                finally { g.pose().popPose(); }
                return;
            }
            if(inside(mx,my,scaleX(),controlY(),scaleGroupW(),CONTROL_H))
                g.renderTooltip(Minecraft.getInstance().font,tr("scale_help"),mx,my);
            else if(inside(mx,my,cheatX(),cheatY(),cheatW(),CHEAT_H))
                g.renderTooltip(Minecraft.getInstance().font,
                        browser_cheat_client.allowed() ? tr("cheat_hint") : tr("cheat_no_permission"), mx, my);
            else if(inside(mx,my,x+2,y+2,15,18))
                g.renderTooltip(Minecraft.getInstance().font,standalone?tr("all_hint",recipe_browser_index.recipes().size()):tr("independent_page"),mx,my);
            else if(inside(mx,my,x+w-33,y+3,18,18))
                g.renderTooltip(Minecraft.getInstance().font,tr("settings_hint",
                        tr("sort."+sort.name().toLowerCase(java.util.Locale.ROOT)),
                        tr("item_scale",recipe_browser_preferences.itemScale()),tr(favoritesOnly?"on":"off")),mx,my);
            else if(inside(mx,my,x+w-15,y+2,14,18))
                g.renderTooltip(Minecraft.getInstance().font,standalone?tr(overlay?"cheat_close":"all_hint",recipe_browser_index.recipes().size()):tr("collapse_hint",recipe_browser_index.recipes().size()),mx,my);
            else if (!hovered.isEmpty()) itemTooltip(g, hovered, null, mx, my, true);
            else if ((!compactControls||w>=110)&&inside(mx, my, x + 17, y+2, Math.max(4,w - 56), 18))
                g.renderTooltip(Minecraft.getInstance().font, tr("all_hint", recipe_browser_index.recipes().size()), mx, my);
            else if (query.isMouseOver(mx, my) && !query.isFocused()) g.renderTooltip(Minecraft.getInstance().font, white_lily_key_display.hint(KEY+"search_help.keys",white_lily_keybinds.BROWSER_SEARCH), mx, my);
        }

        void cheatTooltip(GuiGraphics g,int mx,int my)
        {
            var popup=cheatPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            if(!popup.contains(mx,my))return;
            if(inside(mx,my,px+pw-16,py+2,14,14))
                g.renderTooltip(Minecraft.getInstance().font,tr("cheat_close"),mx,my);
            else if(!browser_cheat_client.allowed())
                g.renderTooltip(Minecraft.getInstance().font,tr("cheat_no_permission"),mx,my);
            else if(inside(mx,my,px+2,py+19,pw-4,16))
                g.renderTooltip(Minecraft.getInstance().font,tr("cheat_take_hint"),mx,my);
            else if(inside(mx,my,px+2,py+37,pw-4,16))
                g.renderTooltip(Minecraft.getInstance().font,tr("cheat_show_all_hint"),mx,my);
        }

        void settingsTooltip(GuiGraphics g,int mx,int my)
        {
            var popup=settingsPopup();int px=popup.x(),py=popup.y(),pw=popup.w(),ph=popup.h();
            if(!popup.contains(mx,my))return;
            if(inside(mx,my,px+pw-16,py+2,14,14))
                g.renderTooltip(Minecraft.getInstance().font,tr("cheat_close"),mx,my);
            else if(inside(mx,my,px+2,py+19,pw-4,16))
                g.renderTooltip(Minecraft.getInstance().font,tr("sort_hint",tr("sort."+sort.name().toLowerCase(java.util.Locale.ROOT))),mx,my);
            else if(inside(mx,my,px+2,py+37,pw-4,16))
                g.renderTooltip(Minecraft.getInstance().font,tr("favorites"),mx,my);
            else if(inside(mx,my,px+2,py+55,pw-4,18))
                g.renderTooltip(Minecraft.getInstance().font,tr("scale_help"),mx,my);
            else if(inside(mx,my,px+4,py+ph-14,pw-8,12))
                g.renderTooltip(Minecraft.getInstance().font,tr("settings_reset_hint"),mx,my);
        }

        boolean click(double mx, double my, int button)
        {
            layout();
            if (compact)
            {
                if (tabVisible && inside(mx, my, x, y, 18, 18))
                {
                    cheatOpen=false;settingsOpen=false;
                    collapsed=false;
                    layout();
                    if(browser_layout.sidebarEntry(compact)==browser_layout.SidebarEntry.OPEN_PAGE)
                        show(new BrowserScreen(parent,true));
                    return true;
                }
                return false;
            }
            // A foreground menu owns every click, including clicks outside its own rectangle.
            if (cheatOpen) return clickCheat(mx, my, button);
            if (settingsOpen) return clickSettings(mx, my, button);
            if (inside(mx,my,cheatX(),cheatY(),cheatW(),CHEAT_H)) return clickCheat(mx,my,button);
            if (!inside(mx, my, x, y, w, h)) { query.setFocused(false); return false; }
            if (query.isMouseOver(mx, my))
            {
                if (button == 1) query.setValue("");
                query.setFocused(true); query.mouseClicked(mx, my, 0); return true;
            }
            query.setFocused(false);
            if (my < y + HEADER_H)
            {
                if (mx < x + 17) { if(standalone) all(parent); else show(new BrowserScreen(parent,false)); return true; }
                if (mx >= x + w - 15) {
                    if(standalone)
                    {
                        if(overlay && parent instanceof BrowserScreen browser) Minecraft.getInstance().setScreen(browser.parent);
                        else all(parent);
                    }
                    else { collapsed = true; cheatOpen=false;settingsOpen=false;layout(); }
                }
                else if (mx >= x + w - 33) { settingsOpen=true; cheatOpen=false; }
                else all(parent);
                return true;
            }
            if (my >= y + h - 18) { changePage(mx < x + w / 2.0 ? -1 : 1); return true; }
            if (inside(mx,my,x,controlY(),w,CONTROL_H))
            {
                if (inside(mx,my,scaleMinusX(),controlY(),scaleMinusW(),CONTROL_H))scaleItems(recipe_browser_preferences.itemScale()-10);
                else if (inside(mx,my,scalePlusX(),controlY(),scalePlusW(),CONTROL_H))scaleItems(recipe_browser_preferences.itemScale()+10);
                else if (inside(mx,my,scalePercentX(),controlY(),scalePercentW(),CONTROL_H))scaleItems(100);
                return true;
            }
            for(GridCell cell:grid.get(page))
            {
                int sx=x+4+cell.cell()%columns*cellSize,sy=y+GRID_TOP+cell.cell()/columns*cellSize;
                if(!cell.stack().isEmpty()&&inside(mx,my,sx,sy,cellSize,cellSize))
                {
                    if(browser_cheat_client.itemGive()&&button==0){browser_cheat_client.requestGive(cell.stack(),Screen.hasShiftDown());return true;}
                    if(button==2||(!browser_cheat_client.itemGive()&&Screen.hasShiftDown())){recipe_browser_preferences.toggle(cell.stack());refreshFilter();}
                    else if(button==0||button==1)open(parent,cell.stack(),button==1);return true;
                }
            }
            return false;
        }
        boolean scroll(double mx, double my, double amount)
        { if(compact)return false;if(cheatOpen||settingsOpen)return true;if(!inside(mx,my,x,y,w,h))return false;
            if(my>=y+h-BOTTOM_RESERVE)return true;
            if(my>=y+GRID_TOP&&grid.get(page).stream().noneMatch(cell->!cell.stack().isEmpty()&&inside(mx,my,x+4+cell.cell()%columns*cellSize,y+GRID_TOP+cell.cell()/columns*cellSize,cellSize,cellSize)))return false; if (amount != 0) changePage(amount > 0 ? -1 : 1); return true; }
    }

    /** Compact-screen fallback. The live menu stays open on the server until the player returns or closes it. */
    private static final class BrowserScreen extends Screen
    {
        final Screen parent;
        final boolean overlay;
        Panel contents;
        BrowserScreen(Screen parent) { this(parent,false); }
        BrowserScreen(Screen parent,boolean overlay) { super(tr(overlay?"temporary_sidebar":"title")); this.parent = parent; this.overlay=overlay; }
        @Override protected void init()
        {
            ItemStack anchor=contents==null?ItemStack.EMPTY:contents.firstItem();
            contents = new Panel(this, true, overlay);contents.locateItem(anchor);
            if(!overlay)addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                    .bounds(Math.max(2, width - 72), height - 23, 66, 20).build());
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial)
        {
            renderBackground(g); super.render(g, mx, my, partial);
            if(!overlay)g.drawCenteredString(font, getTitle(), width / 2, 9, TEXT);
            contents.renderBase(g, mx, my, partial);
            contents.renderForeground(g, mx, my);
        }
        @Override public boolean mouseClicked(double mx, double my, int button)
        { return contents.click(mx, my, button) || super.mouseClicked(mx, my, button); }
        @Override public boolean mouseScrolled(double mx, double my, double amount)
        { return contents.scroll(mx, my, amount) || super.mouseScrolled(mx, my, amount); }
        @Override public boolean charTyped(char codepoint, int modifiers)
        { return contents.query.isFocused() && contents.query.charTyped(codepoint, modifiers) || super.charTyped(codepoint, modifiers); }
        @Override public boolean keyPressed(int key, int scan, int modifiers)
        {
            if (contents.closePopupOnEscape(key)) return true;
            if (contents.query.isFocused())
            {
                if (key == GLFW.GLFW_KEY_ESCAPE) contents.query.setFocused(false);
                else contents.query.keyPressed(key, scan, modifiers);
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
        @Override public void tick()
        {
            if (!live(this)) { Minecraft.getInstance().setScreen(null); return; }
            if(overlay&&parent instanceof AbstractContainerScreen<?> container)
            {
                int cell=browser_layout.cell(recipe_browser_preferences.itemScale());
                int available=width-container.getGuiLeft()-container.getXSize()-4;
                if(available>=Panel.MIN_SIDEBAR_WIDTH&&height-8>=Panel.GRID_TOP+cell+Panel.BOTTOM_RESERVE)
                { Minecraft.getInstance().setScreen(container); return; }
            }
            contents.query.tick();
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    /** A single modal screen; history stores data, never a chain of recipe screens. */
    private static final class RecipeScreen extends Screen
    {
        private static final int INK=0xFF383838,MUTED=0xFF595959;
        final Screen parent;
        ItemStack subject;
        boolean uses,all,tiny;
        final browser_transfer_state transfer=new browser_transfer_state();
        List<Display> matches=List.of(),visible=List.of(),lastSource=List.of();
        List<ResourceLocation> categories=List.of();
        List<browser_layout.Page> pages=List.of(new browser_layout.Page(0,0,0));
        List<Card> cards=List.of();
        List<Button> fills=new ArrayList<>();
        final java.util.Map<Button,Display> fillRecipes=new java.util.LinkedHashMap<>();
        Button back,categoryButton,previous,next,scaleButton;
        int category,recipe,page,x,y,w,h,bodyTop,bodyBottom,scroll;
        static int nextRequest;
        long revision=Long.MIN_VALUE,cycle=System.currentTimeMillis()/1000;
        float scale=1;
        DisplaySlot hovered;
        ItemStack hoveredStack=ItemStack.EMPTY;
        Component notice=Component.empty();
        record Card(Display display,int index,int ox,int oy,int diagramHeight,int height,int plusX,int plusY,List<net.minecraft.util.FormattedCharSequence> lines){}
        private final class FillButton extends Button {
            FillButton(Display display,int bx,int by,int size){super(bx,by,size,size,Component.literal("+"),b->transfer(display),DEFAULT_NARRATION);}
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
                g.pose().pushPose();g.pose().translate(getX(),getY(),0);float factor=getWidth()/18F;g.pose().scale(factor,factor,1);
                panel(g,0,0,18,18);if(active&&inside(mx,my,getX(),getY(),getWidth(),getHeight()))g.fill(1,1,17,17,0xFFCDE0EA);
                g.drawCenteredString(font,"+",9,5,active?INK:0xFF888888);g.pose().popPose();
            }
        }

        RecipeScreen(Screen parent,ItemStack subject,boolean uses,boolean all){super(tr("title"));this.parent=parent;this.subject=subject;this.uses=uses;this.all=all;}
        @Override public Component getTitle(){return tr(all?"all":uses?"uses":"recipes",subject.getHoverName());}
        @Override protected void init(){refresh();}
        browser_history.Query snapshot(){return new browser_history.Query(subject,uses,all,categories.isEmpty()?null:categories.get(Math.min(category,categories.size()-1)),current()==null?null:current().recipe().getId(),scroll,search,listPage,favoritesOnly);}
        void navigate(ItemStack target,boolean uses,boolean all){HISTORY.push(snapshot(),target,uses,all);subject=target.copy();this.uses=uses;this.all=all;category=recipe=scroll=0;notice=Component.empty();refresh();}
        void back(){var q=HISTORY.pop();if(q==null)return;subject=q.subject().copy();uses=q.uses();all=q.all();category=recipe=0;refresh();int cat=categories.indexOf(q.category());if(cat>=0){category=cat;select();}boolean found=false;for(int i=0;i<visible.size();i++)if(visible.get(i).recipe().getId().equals(q.recipe())){recipe=i;found=true;break;}scroll=q.scroll();notice=found?Component.empty():tr("history_unavailable");rebuild();lastDetails=snapshot();}
        void refresh(){ResourceLocation anchor=current()==null?null:current().recipe().getId(),cat=categories.isEmpty()?null:categories.get(Math.min(category,categories.size()-1));lastSource=recipe_browser_index.recipes();revision=recipe_browser_index.revision();matches=all?lastSource:recipe_browser_index.find(subject,uses);categories=matches.stream().map(Display::category).distinct().sorted().toList();category=Math.max(0,categories.indexOf(cat));select();if(anchor!=null)for(int i=0;i<visible.size();i++)if(visible.get(i).recipe().getId().equals(anchor)){recipe=i;break;}rebuild();}
        void select(){visible=categories.isEmpty()?List.of():matches.stream().filter(d->d.category().equals(categories.get(category))).toList();recipe=Math.max(0,Math.min(recipe,visible.size()-1));}
        Display current(){return visible.isEmpty()?null:visible.get(Math.min(recipe,visible.size()-1));}
        AbstractContainerScreen<?> container(){return rootContainer(this);}
        transfer_reason reasonFor(Display d){var mc=Minecraft.getInstance();var root=container();if(root==null||mc.player==null||mc.player.containerMenu!=root.getMenu()||!root.getMenu().stillValid(mc.player))return transfer_reason.MENU_INVALID;if(d==null)return transfer_reason.DATA_CHANGED;if(d.recipe() instanceof processing_recipe){if(!(root.getMenu() instanceof processing_menu))return transfer_reason.WRONG_DEVICE;if(!recipe_browser_index.canFillProcessing(d.recipe().getId()))return transfer_reason.UNREGISTERED;}return browser_transfer.preview(mc.player,d.recipe());}
        List<net.minecraft.util.FormattedCharSequence> lines(Display d,int width){var text=Component.empty();for(var n:d.notes()){if(!text.getString().isEmpty())text.append(" · ");text.append(n);}return font.split(text,Math.max(35,width));}
        void rebuild(){
            clearWidgets();fills.clear();fillRecipes.clear();
            int maxWidth=visible.stream().mapToInt(browser_card_layout::diagramWidth).max().orElse(168);
            int maxHeight=visible.stream().mapToInt(browser_card_layout::diagramHeight).max().orElse(76);
            h=Math.max(1,Math.min(440,height-8));y=(height-h)/2;
            bodyTop=y+65;bodyBottom=y+h-36;
            scale=browser_layout.fitScale(recipe_browser_preferences.recipeScale(),maxWidth,maxHeight,width,Math.max(1,bodyBottom-bodyTop));
            w=Math.min(Math.max(180,Math.round(maxWidth*scale)+20),Math.max(40,width-8));x=(width-w)/2;
            tiny=w<160||h<140;
            if(tiny){cards=List.of();pages=List.of(new browser_layout.Page(0,0,0));page=0;addRenderableWidget(Button.builder(Component.literal("×"),b->onClose()).bounds(x+w-22,y+3,18,18).build());return;}
            // Compact controls are a temporary layout choice; saved scales are never overwritten.
            categoryButton=addRenderableWidget(Button.builder(current()==null?tr("empty_recipes"):current().title(),b->{category=(category+1)%Math.max(1,categories.size());recipe=scroll=0;select();rebuild();}).bounds(x+5,y+23,Math.max(30,w-10),18).build());categoryButton.active=categories.size()>1;
            addRenderableWidget(Button.builder(Component.literal("×"),b->onClose()).bounds(x+w-22,y+3,18,18).build());
            addRenderableWidget(Button.builder(Component.literal("−"),b->scaleRecipe(recipe_browser_preferences.recipeScale()-10)).bounds(x+5,y+44,18,18).build());
            Component scaleTitle=Math.round(scale*100)<recipe_browser_preferences.recipeScale()?tr("recipe_scale_fit",recipe_browser_preferences.recipeScale(),Math.round(scale*100)):tr("recipe_scale",recipe_browser_preferences.recipeScale());
            scaleButton=addRenderableWidget(Button.builder(scaleTitle,b->scaleRecipe(100)).bounds(x+26,y+44,Math.max(24,w-52),18).build());
            addRenderableWidget(Button.builder(Component.literal("+"),b->scaleRecipe(recipe_browser_preferences.recipeScale()+10)).bounds(x+w-23,y+44,18,18).build());
            back=addRenderableWidget(Button.builder(tr("history_back"),b->back()).bounds(x+5,y+h-22,46,18).build());back.active=!HISTORY.isEmpty();
            previous=addRenderableWidget(Button.builder(Component.literal("‹"),b->change(-1)).bounds(x+55,y+h-22,20,18).build());
            next=addRenderableWidget(Button.builder(Component.literal("›"),b->change(1)).bounds(x+w-25,y+h-22,20,18).build());
            List<Integer> heights=new ArrayList<>();List<List<net.minecraft.util.FormattedCharSequence>> notes=new ArrayList<>();
            for(var d:visible){var lines=lines(d,w-16);notes.add(lines);heights.add(browser_card_layout.cardHeight(d,scale));}
            pages=browser_layout.pages(heights,bodyBottom-bodyTop,2);page=browser_layout.locate(pages,recipe);var range=pages.get(page);previous.active=page>0;next.active=page<pages.size()-1;
            List<Card> built=new ArrayList<>();int cy=bodyTop;int content=range.height();scroll=Math.max(0,Math.min(scroll,Math.max(0,content-(bodyBottom-bodyTop))));
            for(int i=range.first();i<range.end();i++){Display d=visible.get(i);int dh=browser_card_layout.diagramHeight(d);int plusX=browser_card_layout.plusX(d),plusY=browser_card_layout.plusY(d);
                Card c=new Card(d,i,x+8,cy-scroll,dh,heights.get(i),plusX,plusY,notes.get(i));built.add(c);
                int bx=c.ox()+Math.round(plusX*scale),by=c.oy()+Math.round(plusY*scale),bs=Math.max(8,Math.round(18*scale));
                // Only full, currently visible controls receive input; oversized tiny-window cards scroll.
                if(by>=bodyTop&&by+bs<=bodyBottom){Button fill=new FillButton(d,bx,by,bs);fills.add(fill);fillRecipes.put(fill,d);addRenderableWidget(fill);}
                cy+=heights.get(i)+2;}
            cards=List.copyOf(built);updateFillButtons();
        }
        void updateFillButtons(){fillRecipes.forEach((button,display)->button.active=!transfer.isWaiting()&&reasonFor(display)==transfer_reason.OK);}
        void scaleRecipe(int value){recipe_browser_preferences.recipeScale(value);rebuild();}
        void change(int direction){if(pages.size()<2)return;int target=Math.max(0,Math.min(pages.size()-1,page+direction));recipe=pages.get(target).first();scroll=0;rebuild();}
        void transfer(Display d){if(transfer.isWaiting()||d==null)return;recipe=visible.indexOf(d);var root=container();if(root==null)return;var reason=reasonFor(d);if(reason!=transfer_reason.OK){notice=Component.translatable(reason.key());return;}int request=++nextRequest;if(!transfer.begin(request,root.getMenu().containerId,d.recipe().getId()))return;notice=Component.empty();recipe_unlocks.requestTransfer(root.getMenu().containerId,d.recipe().getId(),Screen.hasShiftDown(),recipe_browser_index.recipeFingerprint(d.recipe().getId()),request);updateFillButtons();}
        private void panel(GuiGraphics g,int xx,int yy,int ww,int hh){g.fill(xx,yy,xx+ww,yy+hh,0xFFC6C6C6);g.fill(xx,yy,xx+ww-1,yy+1,0xFFFFFFFF);g.fill(xx,yy,xx+1,yy+hh-1,0xFFFFFFFF);g.fill(xx+ww-1,yy,xx+ww,yy+hh,0xFF555555);g.fill(xx,yy+hh-1,xx+ww,yy+hh,0xFF555555);}
        private void slot(GuiGraphics g,int xx,int yy,boolean over){g.fill(xx,yy,xx+18,yy+18,over?0xFFAAAAAA:0xFF8B8B8B);g.fill(xx,yy,xx+18,yy+1,0xFF373737);g.fill(xx,yy,xx+1,yy+18,0xFF373737);g.fill(xx+17,yy,xx+18,yy+18,0xFFFFFFFF);g.fill(xx,yy+17,xx+18,yy+18,0xFFFFFFFF);}
        private void diagram(GuiGraphics g,Display d){int ax=82,ay=35;if(d.recipe() instanceof processing_recipe){ax=124;ay=27;}
            else if(d.recipe() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe){slot(g,24,58,false);g.fill(29,40,37,51,0xFFB68230);g.fill(31,36,35,48,0xFFFFD256);ax=76;ay=35;}
            else if(d.recipe() instanceof CraftingRecipe){int columns=d.recipe() instanceof net.minecraftforge.common.crafting.IShapedRecipe<?> shaped?Math.max(3,shaped.getRecipeWidth()):3,rows=d.recipe() instanceof net.minecraftforge.common.crafting.IShapedRecipe<?> shaped?Math.max(3,shaped.getRecipeHeight()):3;ax=4+columns*20+18;for(int r=0;r<rows;r++)for(int col=0;col<columns;col++)slot(g,4+col*20,10+r*20,false);}
            else if(d.recipe() instanceof net.minecraft.world.item.crafting.SmithingRecipe){ax=99;ay=32;}else if(!(d.recipe() instanceof net.minecraft.world.item.crafting.StonecutterRecipe))return;
            g.fill(ax,ay+5,ax+22,ay+8,0xFF858585);for(int i=0;i<5;i++)g.fill(ax+18+i,ay+1+i,ax+19+i,ay+12-i,0xFF858585);}
        void hover(double mx,double my){hovered=null;hoveredStack=ItemStack.EMPTY;if(!inside(mx,my,x+4,bodyTop,w-8,bodyBottom-bodyTop))return;for(var c:cards)for(var s:c.display().slots())if(browser_layout.hit(mx,my,c.ox(),c.oy(),scale,s.x(),s.y(),18,18)){hovered=s;hoveredStack=s.stack(cycle);recipe=c.index();return;}}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){if(lastSource!=recipe_browser_index.recipes()||revision!=recipe_browser_index.revision())refresh();renderBackground(g);panel(g,x,y,w,h);text(g,getTitle(),x+7,y+7,w-34,INK);if(!Screen.hasShiftDown())cycle=System.currentTimeMillis()/1000;hover(mx,my);
            if(tiny){g.drawWordWrap(font,tr("small_window"),x+5,y+27,Math.max(20,w-10),INK);super.render(g,mx,my,partial);return;}
            g.enableScissor(x+3,bodyTop,x+w-3,bodyBottom);
            for(Card c:cards){panel(g,x+4,c.oy(),w-8,c.height());g.pose().pushPose();g.pose().translate(c.ox(),c.oy(),0);g.pose().scale(scale,scale,1);diagram(g,c.display());
                java.util.Set<String> labels=new java.util.HashSet<>();
                for(var s:c.display().slots()){if((c.display().recipe() instanceof processing_recipe||c.display().recipe() instanceof net.minecraft.world.item.crafting.SmithingRecipe)&&labels.add(s.label().getString()))text(g,s.label(),s.x(),s.y()-10,c.display().recipe() instanceof processing_recipe?44:29,INK);slot(g,s.x(),s.y(),hovered==s);ItemStack stack=s.stack(cycle);g.renderItem(stack,s.x()+1,s.y()+1);g.renderItemDecorations(font,stack,s.x()+1,s.y()+1);}
                g.pose().popPose();int meta=c.oy()+Math.round(c.diagramHeight()*scale)+1;if(!c.lines().isEmpty())g.drawString(font,c.lines().get(0),x+8,meta,INK,false);
            }g.disableScissor();if(visible.isEmpty())g.drawWordWrap(font,tr("empty_recipes"),x+8,bodyTop+8,w-16,INK);
            Component status=transfer.isWaiting()?tr("waiting"):notice;
            text(g,status,x+6,y+h-34,w-12,transfer.isWaiting()?MUTED:status.getString().isEmpty()?MUTED:0xFF9A3B3B);super.render(g,mx,my,partial);g.drawCenteredString(font,Component.literal((page+1)+" / "+pages.size()),x+(w+38)/2,y+h-16,INK);
            if(hovered!=null&&!hoveredStack.isEmpty()){Component label=hovered.label().copy().append(" ×"+hovered.count());if(hovered.ingredient().getItems().length>1)label=label.copy().append(tr("alternatives",hovered.ingredient().getItems().length));itemTooltip(g,hoveredStack,label,mx,my,false);}
            else {boolean hint=false;for(var b:fills)if(inside(mx,my,b.getX(),b.getY(),b.getWidth(),b.getHeight())){Display display=fillRecipes.get(b);if(display!=null){var reason=reasonFor(display);g.renderTooltip(font,transfer.isWaiting()?tr("waiting"):reason==transfer_reason.OK?tr("transfer_hint"):Component.translatable(reason.key()),mx,my);hint=true;}break;}
                if(!hint&&!status.getString().isEmpty()&&inside(mx,my,x+4,y+h-36,w-8,12))g.renderTooltip(font,status,mx,my);
                else if(!hint)for(var c:cards)if(inside(mx,my,x+5,c.oy()+Math.round(c.diagramHeight()*scale),w-10,10)){List<Component> notes=new ArrayList<>(c.display().notes());notes.add(Component.literal(c.display().recipe().getId().toString()));g.renderTooltip(font,notes.stream().flatMap(note->font.split(note,Math.max(40,Math.min(300,width-20))).stream()).toList(),mx,my);break;}
            }
        }
        @Override public boolean mouseClicked(double mx,double my,int button){if(transfer.isWaiting()){if(button==0&&inside(mx,my,x+w-22,y+3,18,18))onClose();return true;}if(button==3&&!HISTORY.isEmpty()){back();return true;}hover(mx,my);if(hovered!=null&&!hoveredStack.isEmpty()){if(button==2){recipe_browser_preferences.toggle(hoveredStack);return true;}if(button==0||button==1){navigate(hoveredStack,button==1,false);return true;}}super.mouseClicked(mx,my,button);return true;}
        @Override public boolean mouseScrolled(double mx,double my,double amount){if(transfer.isWaiting())return true;if(Screen.hasAltDown()&&recipe_browser_tooltips.scroll(amount))return true;if(inside(mx,my,x,bodyTop,w,bodyBottom-bodyTop)&&pages.get(page).height()>bodyBottom-bodyTop){scroll+=(int)(-amount*16);rebuild();return true;}if(inside(mx,my,x,y,w,h)&&amount!=0)change(amount>0?-1:1);return true;}
        boolean shortcut(recipe_browser_key_bridge.Action action){if(transfer.isWaiting())return false;switch(action){case BACK->{if(HISTORY.isEmpty())return false;back();return true;}case PREVIOUS,NEXT->{change(action==recipe_browser_key_bridge.Action.NEXT?1:-1);return true;}case FAVORITE->{if(browser_cheat_client.favoriteConflictsWithTake())return false;ItemStack item=!hoveredStack.isEmpty()?hoveredStack:subject;if(item.isEmpty())return false;recipe_browser_preferences.toggle(item);return true;}case RECIPES,USES->{if(hoveredStack.isEmpty())return false;navigate(hoveredStack,action==recipe_browser_key_bridge.Action.USES,false);return true;}default->{return false;}}}
        private void success(){if(!live(this)){lastDetails=null;Minecraft.getInstance().setScreen(null);return;}lastDetails=snapshot();Screen target=container();Minecraft.getInstance().setScreen(target);}
        @Override public void onClose(){if(live(this)){lastDetails=snapshot();Screen target=container();if(target!=null){Minecraft.getInstance().setScreen(target);return;}}lastDetails=null;Minecraft.getInstance().setScreen(null);}
        @Override public void tick(){
            if(!live(this)){lastDetails=null;Minecraft.getInstance().setScreen(null);return;}
            if(transfer.isWaiting())
            {
                var root=container();var reply=root==null?null:browser_transfer_feedback.take(transfer.request(),root.getMenu().containerId,transfer.recipe());
                if(reply!=null)
                {
                    var result=transfer.accept(reply);
                    if(result==browser_transfer_state.Result.SUCCESS){success();return;}
                    if(result==browser_transfer_state.Result.FAILURE)
                    {
                        notice=transfer.reason()==null?tr("timeout"):Component.translatable(transfer.reason().key());
                        if(transfer.reason()==transfer_reason.MENU_INVALID){lastDetails=null;Minecraft.getInstance().setScreen(null);return;}
                        updateFillButtons();
                    }
                }
                else if(transfer.tick()){notice=tr("timeout");updateFillButtons();}
            }
            if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.tickCount%5==0)updateFillButtons();
        }
        @Override public boolean isPauseScreen(){return false;}
    }
    private recipe_browser() { }
}
