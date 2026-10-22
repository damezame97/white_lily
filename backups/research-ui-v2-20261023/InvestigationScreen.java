package com.damezame97.white_lily.client;

import com.damezame97.white_lily.client.research_client.Category;
import com.damezame97.white_lily.client.research_client.Chapter;
import com.damezame97.white_lily.client.research_client.Material;
import com.damezame97.white_lily.client.research_client.Node;
import com.damezame97.white_lily.client.research_client.Snapshot;
import com.damezame97.white_lily.research.research_network;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三栏调查界面。节点数据只来自服务端裁剪后的展示快照，渲染不参与解锁裁决。 */
public final class InvestigationScreen extends Screen
{
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("white_lily", "textures/gui/research_atlas.png");
    private static final String KEY = research_client.KEY;
    private static final int TEXT = 0xE7E3D8;
    private static final int MUTED = 0xA4A49B;
    private static final int GOLD = 0xE5C66D;
    private static final int GREEN = 0x8BB887;
    private static final Map<String, View> VIEWS = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> GRAY_ICONS = new HashMap<>();
    @Nullable private final Screen parent;
    private Snapshot data;
    private String chapter = "";
    private String category = "";
    private String selected = "";
    private View view;
    private List<Node> nodes = List.of();
    private Map<String, Node> nodeById = Map.of();
    private final Map<String, List<FormattedCharSequence>> labels = new HashMap<>();
    private final List<Detail> details = new ArrayList<>();
    private final List<Sidebar> sidebar = new ArrayList<>();
    private List<FormattedCharSequence> selectedTitle = List.of();
    private List<FormattedCharSequence> screenTitle = List.of();
    private Rect tree = new Rect(0, 0, 0, 0);
    private Rect detail = new Rect(0, 0, 0, 0);
    private int leftWidth;
    private int contentTop;
    private int contentBottom;
    private int detailHeight;
    private int sidebarHeight;
    private double sidebarScroll;
    private double detailScroll;
    private Button research;
    private boolean submitting;
    private boolean closed;
    private boolean mouseDown;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private double originalX;
    private double originalY;
    @Nullable private Node pressedNode;

    public InvestigationScreen(@Nullable Screen parent, Snapshot data)
    {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.data = data;
    }

    public static void clearViews()
    {
        VIEWS.clear();
        clearGrayIcons();
    }

    public static void clearGrayIcons()
    {
        var manager = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation location : GRAY_ICONS.values()) manager.release(location);
        GRAY_ICONS.clear();
    }

    @Override
    protected void init()
    {
        leftWidth = Mth.clamp(width / 6, 70, 124);
        int rightWidth = Mth.clamp(width / 3, 116, 236);
        int bodyHeight = Math.max(90, height - 68);
        tree = new Rect(leftWidth + 12, 36, Math.max(44, width - leftWidth - rightWidth - 26), bodyHeight);
        detail = new Rect(tree.x + tree.w + 4, 36, rightWidth, bodyHeight);
        contentTop = detail.y + 44;
        contentBottom = detail.y + detail.h - 36;
        screenTitle = font.split(title, Math.max(58, tree.x + tree.w - Math.min(62, tree.w) - 20));
        addRenderableWidget(Button.builder(Component.translatable(KEY + "close"), button -> onClose())
                .bounds(width - 48, 10, 38, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(KEY + "reset"), button -> resetView())
                .bounds(tree.x + tree.w - Math.min(62, tree.w), 11, Math.min(62, tree.w), 18).build());
        research = addRenderableWidget(Button.builder(Component.translatable(KEY + "submit"), button -> submit())
                .bounds(detail.x + 6, detail.y + detail.h - 28, detail.w - 12, 20).build());
        chooseInitialChapter();
        rebuildSidebar();
        rebuildNodes();
        rebuildDetails();
    }

    public void update(Snapshot updated)
    {
        data = updated;
        submitting = false;
        if (font == null) return;
        chooseInitialChapter();
        rebuildSidebar();
        rebuildNodes();
        rebuildDetails();
    }

    private void chooseInitialChapter()
    {
        for (Category c : data.categories())
            for (Chapter ch : c.chapters())
                if (c.open() && ch.id().equals(chapter)) { category = c.id(); return; }
        for (Category c : data.categories())
            if (c.open() && !c.chapters().isEmpty())
            {
                category = c.id();
                chapter = c.chapters().get(0).id();
                return;
            }
    }

    private void rebuildSidebar()
    {
        sidebar.clear();
        int y = 45 - (int) sidebarScroll;
        for (Category c : data.categories())
        {
            List<FormattedCharSequence> title = font.split(Component.translatable(c.title()), leftWidth - 13);
            List<FormattedCharSequence> unavailable = c.open() ? List.of() : font.split(Component.translatable(KEY + "unavailable"), leftWidth - 12);
            int height = Math.max(22, title.size() * 10 + 7);
            sidebar.add(new Sidebar(new Rect(10, y, leftWidth - 4, height), title, unavailable, c.open(), false, c.id(), ""));
            y += height + 3;
            if (c.open())
                for (Chapter ch : c.chapters())
                {
                    List<FormattedCharSequence> childTitle = font.split(Component.translatable(ch.title()), leftWidth - 20);
                    int chHeight = Math.max(22, childTitle.size() * 10 + 7);
                    sidebar.add(new Sidebar(new Rect(17, y, leftWidth - 11, chHeight), childTitle, List.of(), true, true, c.id(), ch.id()));
                    y += chHeight + 3;
                }
            else y += unavailable.size() * 10 + 1;
        }
        sidebarHeight = y - 45 + (int) sidebarScroll;
    }

    private void rebuildNodes()
    {
        List<Node> current = new ArrayList<>();
        Map<String, Node> index = new LinkedHashMap<>();
        for (Node n : data.nodes())
            if (n.category().equals(category) && n.chapter().equals(chapter))
            {
                current.add(n);
                index.put(n.id(), n);
            }
        nodes = List.copyOf(current);
        nodeById = Map.copyOf(index);
        labels.clear();
        for (Node n : nodes) labels.put(n.id(), font.split(Component.translatable(n.name()), 84));
        view = VIEWS.computeIfAbsent(category + "/" + chapter, key -> new View());
        if (!view.initialized || (!view.adjusted && (view.visibleNodeCount != nodes.size()
                || view.width != tree.w || view.height != tree.h))) resetView();
        else if (view.width != tree.w || view.height != tree.h)
        {
            view.panX += (tree.w - view.width) / 2.0;
            view.panY += (tree.h - view.height) / 2.0;
            view.width = tree.w;
            view.height = tree.h;
        }
        view.visibleNodeCount = nodes.size();
        if (!nodeById.containsKey(selected)) selected = nodes.isEmpty() ? "" : nodes.get(0).id();
    }

    private void resetView()
    {
        if (view == null) view = new View();
        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        if (!nodes.isEmpty())
        {
            minX = maxX = nodes.get(0).x();
            minY = maxY = nodes.get(0).y();
            for (Node n : nodes)
            {
                minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
                minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y());
            }
        }
        view.zoom = Mth.clamp((tree.w - 32.0) / Math.max(90, maxX - minX + 90), 0.55, 1.0);
        view.panX = tree.w / 2.0 - (minX + maxX) / 2 * view.zoom;
        view.panY = tree.h / 2.0 - (minY + maxY) / 2 * view.zoom - 8;
        view.initialized = true;
        view.adjusted = false;
        view.width = tree.w;
        view.height = tree.h;
        view.visibleNodeCount = nodes.size();
    }

    private void rebuildDetails()
    {
        details.clear();
        detailHeight = 0;
        Node n = nodeById.get(selected);
        selectedTitle = n == null ? List.of() : font.split(Component.translatable(n.name()), detail.w - 35);
        int titleLines = Math.max(1, selectedTitle.size());
        contentTop = detail.y + Math.max(44, titleLines * 10 + 24);
        research.visible = n != null && !n.complete() && !n.state().equals("discover");
        research.active = n != null && n.ready() && !submitting && n.prerequisites().stream().allMatch(research_client.Requirement::complete)
                && n.materials().stream().allMatch(m -> m.owned() >= m.count());
        if (n == null)
        {
            text(Component.translatable(KEY + "select"), MUTED);
            return;
        }
        if (n.state().equals("discover"))
        {
            heading("discovery");
            text(Component.translatable(n.hint()), TEXT);
        }
        else
        {
            if (!n.intro().isEmpty()) text(Component.translatable(n.intro()), TEXT);
            if (!n.prerequisites().isEmpty())
            {
                heading("prerequisites");
                for (var p : n.prerequisites())
                    text(Component.translatable(KEY + (p.complete() ? "met" : "missing"), Component.translatable(p.name())), p.complete() ? GREEN : MUTED);
            }
            if (!n.complete())
            {
                heading("research_materials");
                for (Material m : n.materials()) material(m, true);
                if (!research.active) text(Component.translatable(KEY + (n.ready() ? "need_materials" : "need_prerequisites")), MUTED);
                text(Component.translatable(KEY + "recipe_locked"), GOLD);
            }
            else
            {
                heading("archive");
                for (String line : n.archive()) text(Component.translatable(line), TEXT);
                if (n.recipe() != null)
                {
                    heading("crafting");
                    text(Component.translatable(KEY + "device", Component.translatable(KEY + "device." + n.recipe().device())), MUTED);
                    for (Material m : n.recipe().ingredients()) material(m, false);
                    text(Component.translatable(KEY + "produces"), GOLD);
                    material(new Material(n.recipe().result(), n.recipe().result().getCount(), 0), false);
                    if (n.recipe().ticks() > 0) text(Component.translatable(KEY + "time", n.recipe().ticks() / 20.0), MUTED);
                    if (!n.recipe().returned().isEmpty())
                    {
                        heading("returned");
                        material(new Material(n.recipe().returned(), n.recipe().returned().getCount(), 0), false);
                    }
                    if (!n.recipe().byproduct().isEmpty())
                    {
                        heading("byproduct");
                        material(new Material(n.recipe().byproduct(), n.recipe().byproduct().getCount(), 0), false);
                    }
                }
            }
        }
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailHeight - (contentBottom - contentTop)));
    }

    private void heading(String key)
    {
        text(Component.translatable(KEY + key), GOLD, 5);
    }

    private void text(Component component, int color) { text(component, color, 0); }

    private void text(Component component, int color, int padding)
    {
        List<FormattedCharSequence> lines = font.split(component, Math.max(38, detail.w - 20));
        details.add(new Detail(lines, ItemStack.EMPTY, color, padding, Math.max(10, lines.size() * 11) + padding + 4));
        detailHeight += details.get(details.size() - 1).height;
    }

    private void material(Material material, boolean researchCost)
    {
        Component count = researchCost ? Component.translatable(KEY + "material_owned", material.stack().getHoverName(), material.owned(), material.count())
                : Component.translatable(KEY + "material", material.stack().getHoverName(), material.count());
        List<FormattedCharSequence> lines = font.split(count, Math.max(24, detail.w - 42));
        details.add(new Detail(lines, material.stack(), researchCost && material.owned() < material.count() ? MUTED : TEXT,
                0, Math.max(20, lines.size() * 11 + 4)));
        detailHeight += details.get(details.size() - 1).height;
    }

    private void submit()
    {
        Node n = nodeById.get(selected);
        ResourceLocation id = n == null ? null : ResourceLocation.tryParse(n.id());
        if (id == null || !research.active) return;
        submitting = true;
        research.active = false;
        research_network.requestSubmit(id, data.revision());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        renderBackground(graphics);
        panel(graphics, 4, 4, width - 8, height - 8, 0);
        for (int i = 0; i < screenTitle.size(); i++) graphics.drawString(font, screenTitle.get(i), 12, (screenTitle.size() == 1 ? 15 : 8) + i * 10, TEXT, false);
        panel(graphics, 8, 36, leftWidth, tree.h, 1);
        panel(graphics, tree.x, tree.y, tree.w, tree.h, 3);
        panel(graphics, detail.x, detail.y, detail.w, detail.h, 1);
        renderSidebar(graphics, mouseX, mouseY);
        renderTree(graphics, mouseX, mouseY);
        renderDetails(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFooter(graphics);
        Node hovered = tree.contains(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
        if (hovered != null) graphics.renderTooltip(font, List.of(Component.translatable(hovered.name()).getVisualOrderText(),
                Component.translatable(KEY + "state." + hovered.state()).getVisualOrderText()), mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.enableScissor(8, 36, leftWidth + 8, 36 + tree.h);
        for (Sidebar s : sidebar)
        {
            boolean active = s.chapter.equals(chapter) && s.child;
            if (active || (s.open && s.rect.contains(mouseX, mouseY))) panel(graphics, s.rect.x, s.rect.y, s.rect.w, s.rect.h, active ? 2 : 0);
            int y = s.rect.y + 5;
            for (FormattedCharSequence line : s.lines)
            {
                graphics.drawString(font, line, s.rect.x + 4, y, s.open ? (active ? GOLD : TEXT) : MUTED, false);
                y += 10;
            }
            if (!s.open)
            {
                int lineY = s.rect.y + s.rect.h;
                for (FormattedCharSequence line : s.unavailable)
                {
                    graphics.drawString(font, line, s.rect.x + 4, lineY, 0x77786F, false);
                    lineY += 10;
                }
            }
        }
        graphics.disableScissor();
        if (sidebarHeight > tree.h - 12)
        {
            int track = tree.h - 8;
            int thumb = Math.max(10, track * track / Math.max(track, sidebarHeight));
            int offset = (int) (sidebarScroll / Math.max(1, sidebarHeight - tree.h + 12) * (track - thumb));
            graphics.fill(leftWidth + 4, 40, leftWidth + 6, 40 + track, 0xFF252B25);
            graphics.fill(leftWidth + 4, 40 + offset, leftWidth + 6, 40 + offset + thumb, 0xFF8E9987);
        }
    }

    private void renderTree(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.enableScissor(tree.x + 2, tree.y + 2, tree.x + tree.w - 2, tree.y + tree.h - 2);
        int grid = Math.max(8, (int) (24 * view.zoom));
        int gx = tree.x + Math.floorMod((int) view.panX, grid);
        int gy = tree.y + Math.floorMod((int) view.panY, grid);
        for (int x = gx; x < tree.x + tree.w; x += grid) graphics.fill(x, tree.y + 2, x + 1, tree.y + tree.h - 2, 0xFF343A37);
        for (int y = gy; y < tree.y + tree.h; y += grid) graphics.fill(tree.x + 2, y, tree.x + tree.w - 2, y + 1, 0xFF343A37);
        for (Node n : nodes)
            for (var p : n.prerequisites())
            {
                Node previous = nodeById.get(p.id());
                if (previous == null) continue;
                int x1 = sx(previous.x()), y1 = sy(previous.y()), x2 = sx(n.x()), y2 = sy(n.y());
                if (Math.max(x1, x2) < tree.x || Math.min(x1, x2) > tree.x + tree.w || Math.max(y1, y2) < tree.y || Math.min(y1, y2) > tree.y + tree.h) continue;
                int color = previous.complete() ? 0xFF718E65 : 0xFF65665D;
                int middle = (x1 + x2) / 2;
                graphics.fill(Math.min(x1, middle), y1 - 1, Math.max(x1, middle) + 1, y1 + 1, color);
                graphics.fill(middle - 1, Math.min(y1, y2), middle + 1, Math.max(y1, y2) + 1, color);
                graphics.fill(Math.min(middle, x2), y2 - 1, Math.max(middle, x2) + 1, y2 + 1, color);
            }
        for (Node n : nodes)
        {
            int x = sx(n.x()), y = sy(n.y());
            double size = 30 * view.zoom;
            if (x + 48 * view.zoom < tree.x || x - 48 * view.zoom > tree.x + tree.w || y + 52 * view.zoom < tree.y || y - size > tree.y + tree.h) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale((float) view.zoom, (float) view.zoom, 1);
            if (n.id().equals(selected))
            {
                graphics.fill(-18, -18, 18, -16, 0xFFE5C66D);
                graphics.fill(-18, 16, 18, 18, 0xFFE5C66D);
                graphics.fill(-18, -16, -16, 16, 0xFFE5C66D);
                graphics.fill(16, -16, 18, 16, 0xFFE5C66D);
            }
            int frame = n.complete() ? 2 : n.ready() ? 1 : 0;
            graphics.blit(ATLAS, -15, -15, frame * 32, 32, 30, 30, 256, 256);
            if (n.state().equals("discover") || n.state().equals("locked")) grayIcon(graphics, n.icon(), -8, -8);
            else graphics.renderItem(n.icon(), -8, -8);
            marker(graphics, n.complete() ? 1 : n.ready() ? 2 : 0, 7, 7);
            List<FormattedCharSequence> nodeLabels = labels.get(n.id());
            for (int i = 0; i < nodeLabels.size(); i++)
                graphics.drawCenteredString(font, nodeLabels.get(i), 0, 22 + 10 * i, n.complete() ? GREEN : TEXT);
            graphics.pose().popPose();
        }
        graphics.disableScissor();
    }

    /** 直接以已加载物品模型的粒子纹理生成灰度副本，缓存至退出或资源重载。 */
    private static void grayIcon(GuiGraphics graphics, ItemStack stack, int x, int y)
    {
        if (stack.isEmpty()) return;
        var client = Minecraft.getInstance();
        var sprite = client.getItemRenderer().getModel(stack, client.level, client.player, 0).getParticleIcon();
        ResourceLocation texture = GRAY_ICONS.computeIfAbsent(sprite.contents().name(), key -> {
            NativeImage source = sprite.contents().getOriginalImage();
            NativeImage gray = new NativeImage(sprite.contents().width(), sprite.contents().height(), true);
            for (int py = 0; py < gray.getHeight(); py++)
                for (int px = 0; px < gray.getWidth(); px++)
                {
                    int color = source.getPixelRGBA(px, py);
                    int value = ((color & 255) * 30 + ((color >> 8) & 255) * 59 + ((color >> 16) & 255) * 11) / 100;
                    gray.setPixelRGBA(px, py, (color & 0xFF000000) | value | (value << 8) | (value << 16));
                }
            return client.getTextureManager().register("white_lily_research_gray", new DynamicTexture(gray));
        });
        graphics.blit(texture, x, y, 16, 16, 0, 0, 16, 16, 16, 16);
    }

    private void renderDetails(GuiGraphics graphics)
    {
        Node n = nodeById.get(selected);
        if (n != null)
        {
            if (n.state().equals("discover") || n.state().equals("locked")) grayIcon(graphics, n.icon(), detail.x + 7, detail.y + 9);
            else graphics.renderItem(n.icon(), detail.x + 7, detail.y + 9);
            for (int i = 0; i < selectedTitle.size(); i++) graphics.drawString(font, selectedTitle.get(i), detail.x + 29, detail.y + 7 + i * 10, TEXT, false);
            graphics.drawString(font, Component.translatable(KEY + "state." + n.state()), detail.x + 7, contentTop - 14,
                    n.complete() ? GREEN : n.ready() ? GOLD : MUTED, false);
        }
        graphics.fill(detail.x + 5, contentTop - 3, detail.x + detail.w - 5, contentTop - 2, 0xFF676A62);
        graphics.enableScissor(detail.x + 4, contentTop, detail.x + detail.w - 4, Math.max(contentTop + 1, contentBottom));
        int y = contentTop - (int) detailScroll;
        for (Detail line : details)
        {
            if (y + line.height >= contentTop && y <= contentBottom)
            {
                int textX = detail.x + 8;
                if (!line.icon.isEmpty())
                {
                    graphics.renderItem(line.icon, textX, y + 1);
                    textX += 20;
                }
                for (int i = 0; i < line.lines.size(); i++) graphics.drawString(font, line.lines.get(i), textX, y + line.padding + 2 + i * 11, line.color, false);
            }
            y += line.height;
        }
        graphics.disableScissor();
        if (detailHeight > contentBottom - contentTop)
        {
            int track = Math.max(1, contentBottom - contentTop);
            int thumb = Math.max(12, track * track / Math.max(1, detailHeight));
            int offset = (int) (detailScroll / Math.max(1, detailHeight - track) * (track - thumb));
            graphics.fill(detail.x + detail.w - 5, contentTop, detail.x + detail.w - 3, contentBottom, 0xFF292D2A);
            graphics.fill(detail.x + detail.w - 5, contentTop + offset, detail.x + detail.w - 3, contentTop + offset + thumb, 0xFF9D9F90);
        }
    }

    private void renderFooter(GuiGraphics graphics)
    {
        int y = height - 24;
        marker(graphics, 0, 12, y);
        graphics.drawString(font, Component.translatable(KEY + "legend.locked"), 23, y, MUTED, false);
        int x = 29 + font.width(Component.translatable(KEY + "legend.locked"));
        marker(graphics, 2, x, y);
        graphics.drawString(font, Component.translatable(KEY + "legend.ready"), x + 11, y, GOLD, false);
        x += 17 + font.width(Component.translatable(KEY + "legend.ready"));
        marker(graphics, 1, x, y);
        graphics.drawString(font, Component.translatable(KEY + "legend.complete"), x + 11, y, GREEN, false);
        graphics.drawString(font, Component.translatable(KEY + "controls"), 12, height - 12, MUTED, false);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int type)
    {
        // atlas 为256默认九宫格坐标系；2像素边框确保任意GUI逻辑尺寸都保留硬边。
        graphics.blitNineSliced(ATLAS, x, y, width, height, 2, 16, 16, type * 16, 0);
    }

    private static void marker(GuiGraphics graphics, int kind, int x, int y)
    {
        graphics.blit(ATLAS, x, y, kind * 10, 68, 9, 9, 256, 256);
    }

    private int sx(double world) { return tree.x + (int) Math.round(view.panX + world * view.zoom); }
    private int sy(double world) { return tree.y + (int) Math.round(view.panY + world * view.zoom); }

    @Nullable private Node nodeAt(double mouseX, double mouseY)
    {
        if (!tree.contains(mouseX, mouseY)) return null;
        double worldX = (mouseX - tree.x - view.panX) / view.zoom;
        double worldY = (mouseY - tree.y - view.panY) / view.zoom;
        for (Node n : nodes)
            if (Math.abs(worldX - n.x()) <= 16 && Math.abs(worldY - n.y()) <= 16) return n;
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Sidebar s : sidebar)
            if (s.open && s.rect.contains(mouseX, mouseY) && s.rect.y + s.rect.h < tree.y + tree.h)
            {
                if (s.child)
                {
                    category = s.category;
                    chapter = s.chapter;
                    selected = "";
                    detailScroll = 0;
                    rebuildNodes();
                    rebuildDetails();
                }
                return true;
            }
        if (tree.contains(mouseX, mouseY))
        {
            mouseDown = true;
            dragged = false;
            pressX = mouseX;
            pressY = mouseY;
            originalX = view.panX;
            originalY = view.panY;
            pressedNode = nodeAt(mouseX, mouseY);
            return true;
        }
        return detail.contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy)
    {
        if (!mouseDown || button != 0) return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= 4) dragged = true;
        // 只允许从树的空白处开始平移。拖过节点、侧栏均不产生节点点击。
        if (dragged && pressedNode == null && tree.contains(mouseX, mouseY))
        {
            view.panX = originalX + mouseX - pressX;
            view.panY = originalY + mouseY - pressY;
            view.adjusted = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (button == 0 && mouseDown)
        {
            Node node = nodeAt(mouseX, mouseY);
            if (!dragged && pressedNode != null && node != null && pressedNode.id().equals(node.id()))
            {
                selected = node.id();
                detailScroll = 0;
                rebuildDetails();
            }
            mouseDown = false;
            pressedNode = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount)
    {
        if (mouseX >= 8 && mouseX < leftWidth + 8 && mouseY >= 36 && mouseY < 36 + tree.h)
        {
            sidebarScroll = Mth.clamp(sidebarScroll - amount * 22, 0, Math.max(0, sidebarHeight - tree.h + 12));
            rebuildSidebar();
            return true;
        }
        if (detail.contains(mouseX, mouseY))
        {
            detailScroll = Mth.clamp(detailScroll - amount * 22, 0, Math.max(0, detailHeight - (contentBottom - contentTop)));
            return true;
        }
        if (tree.contains(mouseX, mouseY))
        {
            double x = (mouseX - tree.x - view.panX) / view.zoom;
            double y = (mouseY - tree.y - view.panY) / view.zoom;
            double scale = Mth.clamp(view.zoom * Math.pow(1.12, amount), 0.5, 2.0);
            view.panX = mouseX - tree.x - x * scale;
            view.panY = mouseY - tree.y - y * scale;
            view.zoom = scale;
            view.adjusted = true;
            return true;
        }
        return false;
    }

    @Override
    public void onClose()
    {
        closeSession();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed()
    {
        closeSession();
        super.removed();
    }

    private void closeSession()
    {
        if (closed) return;
        closed = true;
        if (Minecraft.getInstance().getConnection() != null) research_network.requestClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private static final class View
    {
        private double panX;
        private double panY;
        private double zoom = 1;
        private boolean initialized;
        private boolean adjusted;
        private int width;
        private int height;
        private int visibleNodeCount = -1;
    }
    private record Rect(int x, int y, int w, int h)
    {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
    private record Detail(List<FormattedCharSequence> lines, ItemStack icon, int color, int padding, int height) { }
    private record Sidebar(Rect rect, List<FormattedCharSequence> lines, List<FormattedCharSequence> unavailable,
                           boolean open, boolean child, String category, String chapter) { }
}
