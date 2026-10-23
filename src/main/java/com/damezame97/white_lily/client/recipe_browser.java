package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.recipe_unlock_client;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
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
import java.util.Optional;

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

    /** Used by the client mixin to preserve the underlying inventory while inspecting a recipe. */
    public static boolean isOpeningChild() { return openingChild; }
    private static void show(Screen next)
    {
        openingChild = true;
        try { Minecraft.getInstance().setScreen(next); }
        finally { openingChild = false; }
    }

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event)
    {
        if (event.getScreen() instanceof AbstractContainerScreen<?> container) panel = new Panel(container, false);
    }

    private static Panel panel(Screen screen)
    {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return null;
        if (panel == null || panel.parent != container) panel = new Panel(container, false);
        return panel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(ScreenEvent.Render.Post event)
    {
        Panel current = panel(event.getScreen());
        if (current == null) return;
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        current.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        graphics.pose().popPose();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(ScreenEvent.MouseButtonPressed.Pre event)
    {
        Panel current = panel(event.getScreen());
        if (current != null && current.click(event.getMouseX(), event.getMouseY(), event.getButton())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(ScreenEvent.MouseScrolled.Pre event)
    {
        Panel current = panel(event.getScreen());
        if (current != null && current.scroll(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event)
    {
        Panel current = panel(event.getScreen());
        if (current == null) return;
        int key = event.getKeyCode();
        if (current.query.isFocused())
        {
            if (key == GLFW.GLFW_KEY_ESCAPE) current.query.setFocused(false);
            else current.query.keyPressed(key, event.getScanCode(), event.getModifiers());
            event.setCanceled(true); return;
        }
        if (event.getScreen().getFocused() instanceof EditBox edit && edit.isFocused()) return;
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_O)
        { collapsed = !collapsed; current.layout(); event.setCanceled(true); return; }
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_F)
        {
            if (current.compact) show(new BrowserScreen(current.parent));
            else current.query.setFocused(true);
            event.setCanceled(true); return;
        }
        ItemStack hovered = current.hovered();
        if (hovered.isEmpty() && current.parent instanceof AbstractContainerScreen<?> container && container.getSlotUnderMouse() != null)
            hovered = container.getSlotUnderMouse().getItem();
        if (hovered.isEmpty()) return;
        if (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U)
        { open(current.parent, hovered, key == GLFW.GLFW_KEY_U); event.setCanceled(true); }
        else if (key == GLFW.GLFW_KEY_A)
        { recipe_browser_preferences.toggle(hovered); current.refreshFilter(); event.setCanceled(true); }
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
    { panel = null; recipe_browser_index.clear(); recipe_unlock_client.clear(); search = ""; }

    private static void open(Screen parent, ItemStack stack, boolean uses)
    { show(new RecipeScreen(parent, stack.copy(), uses, false)); }
    private static void all(Screen parent)
    { show(new RecipeScreen(parent, ItemStack.EMPTY, false, true)); }

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
        return root != null && mc.player != null && mc.player.isAlive() && !mc.player.isRemoved() && mc.level != null
                && mc.player.containerMenu == root.getMenu();
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
    private static void itemTooltip(GuiGraphics g, ItemStack item, Component label, int mx, int my)
    {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(mc, item));
        if (label != null) lines.add(label);
        lines.add(tr("item_controls"));
        g.renderTooltip(mc.font, lines, Optional.empty(), item, mx, my);
    }

    private static final class Panel
    {
        final Screen parent;
        final boolean standalone;
        final EditBox query;
        int x, y, w, h, columns, rows, page;
        boolean compact, tabVisible;
        long revision = Long.MIN_VALUE;
        List<ItemStack> source = List.of(), filtered = List.of();
        ItemStack hovered = ItemStack.EMPTY;

        Panel(Screen parent, boolean standalone)
        {
            this.parent = parent; this.standalone = standalone;
            query = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 16, tr("search"));
            query.setMaxLength(160); query.setHint(tr("search_hint")); query.setValue(search);
            query.setResponder(value -> { search = value; page = 0; refreshFilter(); });
            layout(); refreshFilter();
        }

        void layout()
        {
            if (standalone)
            { x = 8; y = 26; w = Math.max(40, parent.width - 16); h = Math.max(60, parent.height - 54); compact = false; tabVisible = true; }
            else
            {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) parent;
                int right = screen.getGuiLeft() + screen.getXSize();
                int available = parent.width - right - 4;
                compact = collapsed || available < 46;
                x = right + 2; y = 4; w = Math.min(146, available); h = Math.max(40, parent.height - 8);
                tabVisible = true;
                if (compact)
                {
                    w = 18; h = 18; y = Math.max(2, screen.getGuiTop());
                    if (available < 18)
                    {
                        x = parent.width - 20;
                        if (screen.getGuiTop() >= 20) y = screen.getGuiTop() - 19;
                        else if (parent.height - screen.getGuiTop() - screen.getYSize() >= 20) y = screen.getGuiTop() + screen.getYSize() + 1;
                        else tabVisible = false;
                    }
                }
            }
            columns = Math.max(1, (w - 8) / 18); rows = Math.max(1, (h - 62) / 18);
            query.setX(x + 4); query.setY(y + 23); query.setWidth(Math.max(10, w - 8));
            if (compact) query.setFocused(false);
        }
        void refreshFilter()
        {
            source = recipe_browser_index.items(); revision = recipe_unlock_client.revision();
            filtered = source.stream().filter(stack -> !favoritesOnly || recipe_browser_preferences.contains(stack))
                    .filter(stack -> recipe_browser_index.matches(stack, query.getValue())).toList();
            page = Math.min(page, pages() - 1);
        }
        int pages() { return Math.max(1, (filtered.size() + columns * rows - 1) / (columns * rows)); }
        void changePage(int direction) { page = Math.floorMod(page + direction, pages()); }
        ItemStack hovered() { return hovered; }

        void render(GuiGraphics g, int mx, int my, float partial)
        {
            layout();
            if (source != recipe_browser_index.items() || revision != recipe_unlock_client.revision()) refreshFilter();
            hovered = ItemStack.EMPTY;
            if (compact)
            {
                if (!tabVisible) return;
                box(g, x, y, 18, 18); g.drawCenteredString(Minecraft.getInstance().font, "≡", x + 9, y + 5, GOLD);
                if (inside(mx, my, x, y, 18, 18)) g.renderTooltip(Minecraft.getInstance().font, tr("open_hint"), mx, my);
                return;
            }
            box(g, x, y, w, h);
            g.drawString(Minecraft.getInstance().font, "≡", x + 5, y + 7, TEXT, false);
            text(g, tr("title"), x + 17, y + 7, w - 44, TEXT);
            g.drawString(Minecraft.getInstance().font, favoritesOnly ? "★" : "☆", x + w - 25, y + 7, GOLD, false);
            g.drawString(Minecraft.getInstance().font, standalone ? "?" : "−", x + w - 12, y + 7, TEXT, false);
            query.render(g, mx, my, partial);
            int start = page * columns * rows;
            for (int cell = 0; cell < columns * rows && start + cell < filtered.size(); cell++)
            {
                ItemStack stack = filtered.get(start + cell);
                int sx = x + 4 + (cell % columns) * 18, sy = y + 43 + (cell / columns) * 18;
                if (inside(mx, my, sx, sy, 18, 18)) { hovered = stack; g.fill(sx, sy, sx + 18, sy + 18, 0xFF5B6674); }
                g.renderItem(stack, sx + 1, sy + 1);
                if (recipe_browser_preferences.contains(stack)) g.fill(sx + 13, sy + 1, sx + 17, sy + 4, GOLD);
            }
            int footer = y + h - 15;
            g.drawString(Minecraft.getInstance().font, "‹", x + 5, footer + 2, TEXT, false);
            text(g, Component.literal((page + 1) + "/" + pages()), x + 16, footer + 2, w - 32, TEXT);
            g.drawString(Minecraft.getInstance().font, "›", x + w - 10, footer + 2, TEXT, false);
            if (filtered.isEmpty()) text(g, tr("empty"), x + 5, y + 46, w - 10, EDGE);
            if (!hovered.isEmpty()) itemTooltip(g, hovered, null, mx, my);
            else if (inside(mx, my, x + w - 29, y + 2, 14, 18)) g.renderTooltip(Minecraft.getInstance().font, tr("favorites"), mx, my);
            else if (inside(mx, my, x, y, w - 30, 20)) g.renderTooltip(Minecraft.getInstance().font, tr("all_hint", recipe_browser_index.recipes().size()), mx, my);
            else if (query.isMouseOver(mx, my) && !query.isFocused()) g.renderTooltip(Minecraft.getInstance().font, tr("search_help"), mx, my);
        }

        boolean click(double mx, double my, int button)
        {
            layout();
            if (compact)
            {
                if (tabVisible && inside(mx, my, x, y, 18, 18))
                { show(new BrowserScreen(parent)); return true; }
                return false;
            }
            if (!inside(mx, my, x, y, w, h)) { query.setFocused(false); return false; }
            if (query.isMouseOver(mx, my))
            {
                if (button == 1) query.setValue("");
                query.setFocused(true); query.mouseClicked(mx, my, 0); return true;
            }
            query.setFocused(false);
            if (my < y + 21)
            {
                if (mx >= x + w - 15) { if (standalone) all(parent); else { collapsed = true; layout(); } }
                else if (mx >= x + w - 30) { favoritesOnly = !favoritesOnly; page = 0; refreshFilter(); }
                else all(parent);
                return true;
            }
            if (my >= y + h - 18) { changePage(mx < x + w / 2.0 ? -1 : 1); return true; }
            int cellX = ((int) mx - x - 4) / 18, cellY = ((int) my - y - 43) / 18;
            if (mx >= x + 4 && my >= y + 43 && cellX < columns && cellY >= 0 && cellY < rows)
            {
                int index = page * columns * rows + cellY * columns + cellX;
                if (index < filtered.size())
                {
                    ItemStack stack = filtered.get(index);
                    if (button == 2 || Screen.hasShiftDown()) { recipe_browser_preferences.toggle(stack); refreshFilter(); }
                    else if (button == 0 || button == 1) open(parent, stack, button == 1);
                }
            }
            return true;
        }
        boolean scroll(double mx, double my, double amount)
        { if (compact || !inside(mx, my, x, y, w, h)) return false; if (amount != 0) changePage(amount > 0 ? -1 : 1); return true; }
    }

    /** Compact-screen fallback. The live menu stays open on the server until the player returns or closes it. */
    private static final class BrowserScreen extends Screen
    {
        final Screen parent;
        Panel contents;
        BrowserScreen(Screen parent) { super(tr("title")); this.parent = parent; }
        @Override protected void init()
        {
            contents = new Panel(this, true);
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                    .bounds(Math.max(2, width - 72), height - 23, 66, 20).build());
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial)
        {
            renderBackground(g); super.render(g, mx, my, partial);
            g.drawCenteredString(font, getTitle(), width / 2, 9, TEXT);
            contents.render(g, mx, my, partial);
        }
        @Override public boolean mouseClicked(double mx, double my, int button)
        { return contents.click(mx, my, button) || super.mouseClicked(mx, my, button); }
        @Override public boolean mouseScrolled(double mx, double my, double amount)
        { return contents.scroll(mx, my, amount) || super.mouseScrolled(mx, my, amount); }
        @Override public boolean charTyped(char codepoint, int modifiers)
        { return contents.query.isFocused() && contents.query.charTyped(codepoint, modifiers) || super.charTyped(codepoint, modifiers); }
        @Override public boolean keyPressed(int key, int scan, int modifiers)
        {
            if (contents.query.isFocused())
            {
                if (key == GLFW.GLFW_KEY_ESCAPE) contents.query.setFocused(false);
                else contents.query.keyPressed(key, scan, modifiers);
                return true;
            }
            if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_F) { contents.query.setFocused(true); return true; }
            ItemStack hovered = contents.hovered();
            if (!hovered.isEmpty() && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U))
            { open(this, hovered, key == GLFW.GLFW_KEY_U); return true; }
            if (!hovered.isEmpty() && key == GLFW.GLFW_KEY_A)
            { recipe_browser_preferences.toggle(hovered); contents.refreshFilter(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
        @Override public void tick()
        {
            if (!live(this)) { Minecraft.getInstance().setScreen(null); return; }
            contents.query.tick();
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private static final class RecipeScreen extends Screen
    {
        final Screen parent;
        final ItemStack subject;
        final boolean uses, all;
        List<recipe_browser_index.Display> matches = List.of(), visible = List.of();
        List<net.minecraft.resources.ResourceLocation> categories = List.of();
        int category, recipe, x, y, w, h, bodyTop, bodyBottom, scroll;
        long revision = Long.MIN_VALUE;
        List<recipe_browser_index.Display> lastSource = List.of();
        recipe_browser_index.DisplaySlot hovered;
        ItemStack hoveredStack = ItemStack.EMPTY;
        long cycle = System.currentTimeMillis() / 1000;
        Button transfer, categoryButton, previous, next, favorite;
        Component notice = Component.empty();

        RecipeScreen(Screen parent, ItemStack subject, boolean uses, boolean all)
        {
            super(tr(all ? "all" : uses ? "uses" : "recipes", subject.getHoverName()));
            this.parent = parent; this.subject = subject; this.uses = uses; this.all = all;
        }
        @Override protected void init()
        {
            w = Math.min(310, width - 12); h = Math.min(292, height - 12); x = (width - w) / 2; y = (height - h) / 2;
            bodyTop = y + 64; bodyBottom = y + h - 48;
            categoryButton = addRenderableWidget(Button.builder(Component.empty(), button -> { category = (category + 1) % Math.max(1, categories.size()); recipe = 0; scroll = 0; select(); })
                    .bounds(x + 6, y + 22, Math.max(20, w - 12), 18).build());
            previous = addRenderableWidget(Button.builder(Component.literal("‹"), button -> change(-1)).bounds(x + 6, y + 43, 22, 18).build());
            next = addRenderableWidget(Button.builder(Component.literal("›"), button -> change(1)).bounds(x + w - 28, y + 43, 22, 18).build());
            transfer = addRenderableWidget(Button.builder(tr("transfer"), button -> transfer()).bounds(x + 6, y + h - 24, Math.max(30, w - 104), 18).build());
            favorite = addRenderableWidget(Button.builder(Component.literal("☆"), button -> toggleFavorite()).bounds(x + w - 94, y + h - 24, 22, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose()).bounds(x + w - 68, y + h - 24, 62, 18).build());
            refresh();
        }
        void refresh()
        {
            lastSource = recipe_browser_index.recipes(); revision = recipe_unlock_client.revision();
            matches = all ? lastSource : recipe_browser_index.find(subject, uses);
            categories = matches.stream().map(recipe_browser_index.Display::category).distinct().sorted().toList();
            category = Math.min(category, Math.max(0, categories.size() - 1)); select();
        }
        void select()
        {
            visible = categories.isEmpty() ? List.of() : matches.stream().filter(display -> display.category().equals(categories.get(category))).toList();
            recipe = Math.min(recipe, Math.max(0, visible.size() - 1));
            recipe_browser_index.Display display = current();
            categoryButton.setMessage(display == null ? tr("empty_recipes") : display.title().copy().append("  " + (category + 1) + "/" + categories.size()));
            categoryButton.active = categories.size() > 1;
            previous.active = next.active = visible.size() > 1;
            transfer.active = canTransfer(display);
            ItemStack bookmark = bookmark(); favorite.active = !bookmark.isEmpty();
            favorite.setMessage(Component.literal(!bookmark.isEmpty() && recipe_browser_preferences.contains(bookmark) ? "★" : "☆"));
        }
        @Nullable recipe_browser_index.Display current() { return visible.isEmpty() ? null : visible.get(recipe); }
        void change(int direction) { if (!visible.isEmpty()) { recipe = Math.floorMod(recipe + direction, visible.size()); scroll = 0; notice = Component.empty(); select(); } }
        ItemStack bookmark()
        {
            if (!subject.isEmpty()) return subject;
            return current() != null && !current().outputs().isEmpty() ? current().outputs().get(0) : ItemStack.EMPTY;
        }
        void toggleFavorite() { ItemStack item = bookmark(); if (!item.isEmpty()) recipe_browser_preferences.toggle(item); select(); }
        @Nullable AbstractContainerScreen<?> container()
        { return rootContainer(this); }
        boolean canTransfer(@Nullable recipe_browser_index.Display display)
        {
            Minecraft mc = Minecraft.getInstance();
            AbstractContainerScreen<?> screen = container();
            if (display == null || screen == null || mc.player == null || mc.gameMode == null
                    || mc.player.containerMenu != screen.getMenu() || !recipe_unlock_client.isUnlocked(display.recipe().getId())) return false;
            if (display.recipe() instanceof processing_recipe && screen.getMenu() instanceof processing_menu) return true;
            if (!(display.recipe() instanceof CraftingRecipe) || display.recipe().isSpecial()) return false;
            if (!(screen.getMenu() instanceof CraftingMenu) && !(screen.getMenu() instanceof InventoryMenu)) return false;
            RecipeBookMenu<?> menu = (RecipeBookMenu<?>) screen.getMenu();
            return display.recipe().canCraftInDimensions(menu.getGridWidth(), menu.getGridHeight());
        }
        void transfer()
        {
            recipe_browser_index.Display display = current();
            AbstractContainerScreen<?> screen = container();
            if (!canTransfer(display) || screen == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (display.recipe() instanceof processing_recipe)
                recipe_unlocks.requestTransfer(screen.getMenu().containerId, display.recipe().getId(), Screen.hasShiftDown());
            else mc.gameMode.handlePlaceRecipe(screen.getMenu().containerId, display.recipe(), Screen.hasShiftDown());
            mc.setScreen(screen);
        }
        @Override public void render(GuiGraphics g, int mx, int my, float partial)
        {
            if (lastSource != recipe_browser_index.recipes() || revision != recipe_unlock_client.revision()) refresh();
            select(); renderBackground(g); box(g, x, y, w, h);
            text(g, getTitle(), x + 7, y + 8, w - 14, GOLD);
            recipe_browser_index.Display display = current();
            hovered = null; hoveredStack = ItemStack.EMPTY;
            if (!Screen.hasShiftDown()) cycle = System.currentTimeMillis() / 1000;
            if (display == null) g.drawWordWrap(font, tr("empty_recipes"), x + 10, bodyTop + 12, w - 20, TEXT);
            else
            {
                g.drawCenteredString(font, tr("recipe_page", recipe + 1, visible.size()), x + w / 2, y + 48, TEXT);
                if (!display.workstation().isEmpty()) g.renderItem(display.workstation(), x + w - 49, y + 44);
                int widest = display.slots().stream().mapToInt(slot -> slot.x() + 20).max().orElse(140);
                float scale = Math.min(1F, (w - 22F) / Math.max(140, widest));
                int content = Math.round((display.contentHeight() + display.notes().size() * 11 + 22) * scale);
                scroll = Math.max(0, Math.min(scroll, Math.max(0, content - (bodyBottom - bodyTop))));
                g.enableScissor(x + 5, bodyTop, x + w - 5, bodyBottom);
                g.pose().pushPose(); g.pose().translate(x + 10, bodyTop - scroll, 0); g.pose().scale(scale, scale, 1);
                for (recipe_browser_index.DisplaySlot slot : display.slots())
                {
                    ItemStack stack = slot.stack(cycle);
                    boolean over = my >= bodyTop && my < bodyBottom && inside((mx - x - 10) / scale, (my - bodyTop + scroll) / scale, slot.x(), slot.y(), 18, 18);
                    g.fill(slot.x(), slot.y(), slot.x() + 18, slot.y() + 18, over ? 0xFF66788E : 0xFF121820);
                    if (slot.output()) g.renderOutline(slot.x(), slot.y(), 18, 18, 0xFF9D8B51);
                    g.renderItem(stack, slot.x() + 1, slot.y() + 1);
                    g.renderItemDecorations(font, stack, slot.x() + 1, slot.y() + 1);
                    if (over) { hovered = slot; hoveredStack = stack; }
                }
                for (int i = 0; i < display.notes().size(); i++)
                    text(g, display.notes().get(i), 4, display.contentHeight() + i * 11, Math.round((w - 24) / scale), TEXT);
                text(g, Component.literal(display.recipe().getId().toString()), 4, display.contentHeight() + display.notes().size() * 11 + 4, Math.round((w - 24) / scale), EDGE);
                g.pose().popPose(); g.disableScissor();
                if (content > bodyBottom - bodyTop) g.fill(x + w - 5, bodyTop, x + w - 3, bodyBottom, EDGE);
            }
            text(g, notice.getString().isEmpty() ? tr("detail_help") : notice, x + 7, y + h - 43, w - 14, EDGE);
            super.render(g, mx, my, partial);
            if (hovered != null && !hoveredStack.isEmpty())
            {
                Component label = hovered.label().copy().append(" ×" + hovered.count());
                if (hovered.ingredient().getItems().length > 1) label = label.copy().append(tr("alternatives", hovered.ingredient().getItems().length));
                itemTooltip(g, hoveredStack, label, mx, my);
            }
            else if (transfer.isMouseOver(mx, my)) g.renderTooltip(font, tr(transfer.active ? "transfer_hint" : "transfer_unavailable"), mx, my);
            else if (display != null && !display.workstation().isEmpty() && inside(mx, my, x + w - 50, y + 43, 18, 18))
                g.renderTooltip(font, display.workstation(), mx, my);
            else if (categoryButton.isMouseOver(mx, my)) g.renderTooltip(font, tr("category_hint"), mx, my);
        }
        @Override public boolean mouseClicked(double mx, double my, int button)
        {
            if (hovered != null && !hoveredStack.isEmpty() && my >= bodyTop && my < bodyBottom)
            {
                if (button == 2) { recipe_browser_preferences.toggle(hoveredStack); select(); }
                else if (button == 0 || button == 1) open(this, hoveredStack, button == 1);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        @Override public boolean mouseScrolled(double mx, double my, double amount)
        {
            if (inside(mx, my, x, bodyTop, w, bodyBottom - bodyTop)) { scroll += (int) (-amount * 22); return true; }
            if (inside(mx, my, x, y, w, h)) { change(amount > 0 ? -1 : 1); return true; }
            return super.mouseScrolled(mx, my, amount);
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers)
        {
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) { change(1); return true; }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) { change(-1); return true; }
            if (key == GLFW.GLFW_KEY_A) { if (!hoveredStack.isEmpty()) recipe_browser_preferences.toggle(hoveredStack); else toggleFavorite(); return true; }
            if (!hoveredStack.isEmpty() && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U))
            { open(this, hoveredStack, key == GLFW.GLFW_KEY_U); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
        @Override public void tick() { if (!live(this)) Minecraft.getInstance().setScreen(null); }
        @Override public boolean isPauseScreen() { return false; }
    }
    private recipe_browser() { }
}
