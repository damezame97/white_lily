package com.damezame97.white_lily.client;

import com.damezame97.white_lily.gamerule.happy_mode;
import com.damezame97.white_lily.client.NoiseTreeRenderer.Link;
import com.damezame97.white_lily.client.NoiseTreeRenderer.Node;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameRules;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import com.damezame97.white_lily.client.SpecialWindowTree.Connection;
import com.damezame97.white_lily.client.SpecialWindowTree.NodeSpec;
import com.damezame97.white_lily.client.SpecialWindowTree.Point;
import com.damezame97.white_lily.client.SpecialWindowTree.Selection;

/**
 * 噪域节点界面：悬停描述由中心噪点展开、离开时快速坍缩；节点在约一秒内蓝红重组。
 *
 * <p>【调试入口】节点 ID、坐标、描述语言键、前置与折线中转点统一在
 * {@link SpecialWindowTree#defaultDefinition()} 配置；这里不另维护一份隐形依赖。
 * 所有输入先转换到同一个世界坐标，缩放/拖拽后仍命中实际节点。
 * 颜色、光晕、碎块频率在 {@link NoiseTreeRenderer} 与 noise_tree.fsh 中调节。</p>
 *
 * <p>选择仅属于当前创建世界草稿。WeakHashMap 让退出/重进本界面保留同一份选择，
 * 另建世界不会继承，也不会阻止已取消草稿回收。原点始终从原版 Happy_mode 规则同步；
 * 普通节点目前是依赖选择示例，后续游戏效果在 toggleNode 中标明的入口接入。</p>
 */
public final class SpecialWindowScreen extends Screen {
    // 【动画调试】单位毫秒：打开/收束时长与 Esc 长按阈值。转场结束前禁止节点点击。
    private static final long OPEN_MS = 900;
    private static final long CLOSE_MS = 600;
    private static final long ESC_HOLD_MS = 900;
    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 2.75F;
    // 【镜头调试】默认缩放，以及 Home/0 归中后的缩放；拖拽偏移以屏幕 GUI 像素计。
    private static final float DEFAULT_ZOOM = 0.62F;
    private static final SpecialWindowTree.Definition TREE = SpecialWindowTree.defaultDefinition();
    private static final Map<CreateWorldScreen, Selection> DRAFT_SELECTIONS = new WeakHashMap<>();
    // 【命中范围调试】单位是树的世界坐标，跟随 zoom；与 shader 曝光核半径相对应。
    private static final float ROOT_HIT_RADIUS = 28;
    private static final float NODE_HIT_RADIUS = 24;
    // 【动态视觉调试】节点切换约1秒；两端激活进度共同驱动粗噪带收束为静态白线。
    private static final float NODE_TRANSITION_SECONDS = 1.0F;
    private static final float TOOLTIP_OPEN_SECONDS = 0.42F;
    private static final float TOOLTIP_CLOSE_SECONDS = 0.18F;
    private static final int TOOLTIP_PADDING = 10;
    private static final int TOOLTIP_MARGIN = 5;
    private static final int TOOLTIP_MAX_WIDTH = 240;
    private final CreateWorldScreen parent;
    private final Selection selection;
    private final Map<String, NoiseTransition> nodeTransitions = new LinkedHashMap<>();
    private final NoiseDocumentMotion documentMotion = new NoiseDocumentMotion(
            TOOLTIP_OPEN_SECONDS, TOOLTIP_CLOSE_SECONDS);
    private final NoiseCursorTrail cursorTrail = new NoiseCursorTrail();
    private List<Node> renderedNodes = List.of();
    private List<Link> renderedLinks = List.of();
    private NodeSpec hoveredNode;
    private final long openedAt = Util.getMillis();
    private long lastFrameAt = openedAt;
    private long closingAt = -1;
    private long escapeHeldAt = -1;
    private long toggledAt = -10000;
    private float zoom = DEFAULT_ZOOM;
    private float targetZoom = DEFAULT_ZOOM;
    private float panX;
    private float panY;
    private float originHover;
    private boolean dragging;
    private boolean originKeyHeld;

    public SpecialWindowScreen(CreateWorldScreen parent) {
        super(Component.translatable("white_lily.special_window.title"));
        this.parent = parent;
        this.selection = DRAFT_SELECTIONS.computeIfAbsent(parent, screen -> {
            Selection draft = new Selection(TREE);
            // 原版规则页也能修改 Happy_mode：必须观察每次提交，而不是只比较重新进入时的
            // 最终值。这样“在原版页关根 → 又开根”仍会关闭后代，不会悄悄恢复旧选择。
            // 回调只捕获 draft，不捕获 screen/parent，避免反向强引用破坏 WeakHashMap 回收。
            screen.getUiState().addListener(state -> draft.setRootEnabled(
                    state.getGameRules().getBoolean(happy_mode.RULE)));
            return draft;
        });
        synchronizeRoot();
        rebuildNodes();
    }

    @Override
    protected void init() {
        super.init();
        // 真正进入三击入口打开的窗口才记录彩蛋；本次启动以后返回主菜单必定显示黑白噪域。
        // init 在 resize 时重入也无妨：访问标记幂等，不修改当前草稿或 Happy_mode。
        NoiseTitleEasterEgg.visitedSpecialWindow();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float frameSeconds = Math.max(0, (now - lastFrameAt) / 1000.0F);
        float dt = Mth.clamp(frameSeconds, 0.0F, 0.10F);
        lastFrameAt = now;
        synchronizeRoot();
        rebuildNodes();
        if (!minecraft.isWindowActive()
                || !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)) {
            originKeyHeld = false;
        }
        if (!minecraft.isWindowActive()
                || !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            escapeHeldAt = -1;
        }
        if (escapeHeldAt >= 0 && now - escapeHeldAt >= ESC_HOLD_MS) onClose();
        // Keep consuming Escape repeats until release; otherwise a long hold would
        // immediately cancel the vanilla CreateWorldScreen after returning to it.
        if (closingAt >= 0 && now - closingAt >= CLOSE_MS
                && !InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            minecraft.setScreen(parent);
            return;
        }

        float open = ease(Mth.clamp((now - openedAt) / (float) OPEN_MS, 0, 1));
        float close = closingAt < 0 ? 1.0F
                : 1.0F - ease(Mth.clamp((now - closingAt) / (float) CLOSE_MS, 0, 1));
        float reveal = open * close;
        float time = (now - openedAt) / 1000.0F;
        zoom += (targetZoom - zoom) * (1.0F - (float) Math.exp(-dt * 13));
        hoveredNode = nodeAt(mouseX, mouseY);
        boolean originHovered = hoveredNode != null && SpecialWindowTree.ROOT_ID.equals(hoveredNode.id());
        originHover += ((originHovered ? 1.0F : 0.0F) - originHover)
                * (1.0F - (float) Math.exp(-dt * 10));

        if (reveal < 0.995F) {
            // Rendering the retained screen is visual only: input stays on this screen.
            parent.render(graphics, -1000, -1000, partialTick);
        }
        graphics.enableScissor(0, 0, width, height);
        graphics.fill(0, 0, width, height, color(0x020712, reveal));
        float pulse = Math.max(0.0F, 1.0F - (now - toggledAt) / 1200.0F);
        cursorTrail.sample(mouseX, mouseY, time, minecraft.isWindowActive()
                && now - openedAt >= OPEN_MS && closingAt < 0);
        NoiseFieldRenderer.render(graphics, 0, 0, width, height, time,
                mouseX, mouseY, 0.18F + originHover * 0.25F + pulse * 0.55F
                        + (1.0F - reveal) * 1.3F, reveal, false, cursorTrail.samples(time));

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F + panX, height / 2.0F + panY, 0);
        float openingZoom = zoom * (0.84F + reveal * 0.16F);
        graphics.pose().scale(openingZoom, openingZoom, 1);
        NoiseTreeRenderer.render(graphics, renderedLinks, renderedNodes, time,
                worldX(mouseX), worldY(mouseY), originHover * 0.25F + pulse * 0.75F,
                reveal, selection.isEnabled(SpecialWindowTree.ROOT_ID));
        graphics.pose().popPose();
        graphics.disableScissor();
        // 离开后保留旧文件0.18秒收拢；不是瞬间消失。文字由中心展开完成后再显露。
        boolean mayDescribe = !dragging && closingAt < 0 && now - openedAt >= OPEN_MS
                && minecraft.isWindowActive();
        documentMotion.update(mayDescribe && hoveredNode != null ? hoveredNode.id() : null,
                frameSeconds, mouseX, mouseY);
        if (documentMotion.visibleId() != null && documentMotion.amount() > 0) {
            renderNodeDescription(graphics, TREE.node(documentMotion.visibleId()),
                    documentMotion.anchorX(), documentMotion.anchorY(), documentMotion.amount(), time);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closingAt >= 0 || Util.getMillis() - openedAt < OPEN_MS) return true;
        if (button == 0) {
            NodeSpec clicked = nodeAt(mouseX, mouseY);
            if (clicked != null) {
                toggleNode(clicked.id());
                // 锁定节点也吞掉点击，避免误把一次解锁尝试变成画布拖拽。
                return true;
            }
        }
        if (button == 0 || button == 2) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && closingAt < 0) {
            panX += (float) dx;
            panY += (float) dy;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (closingAt < 0) targetZoom = Mth.clamp(targetZoom * (delta > 0 ? 1.15F : 1 / 1.15F),
                MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (escapeHeldAt < 0 && closingAt < 0) escapeHeldAt = Util.getMillis();
            return true;
        }
        if (closingAt >= 0) return true;
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            targetZoom = Mth.clamp(targetZoom * 1.15F, MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            targetZoom = Mth.clamp(targetZoom / 1.15F, MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_0) {
            panX = panY = 0;
            targetZoom = DEFAULT_ZOOM;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (!originKeyHeld && Util.getMillis() - openedAt >= OPEN_MS) {
                // 空格保留为原点快捷键；普通节点用实时鼠标命中，避免上一帧悬停残留。
                toggleNode(SpecialWindowTree.ROOT_ID);
            }
            originKeyHeld = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            originKeyHeld = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            escapeHeldAt = -1;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void resize(net.minecraft.client.Minecraft client, int width, int height) {
        cursorTrail.clear();
        parent.resize(client, width, height);
        super.resize(client, width, height);
    }

    @Override
    public void onClose() {
        if (closingAt < 0) {
            closingAt = Util.getMillis();
            dragging = false;
        }
    }

    private GameRules currentRules() {
        // 原版“更多 → 游戏规则”完成时会替换 GameRules 对象，不能永久抓住旧引用。
        return parent.getUiState().getGameRules();
    }

    private void synchronizeRoot() {
        boolean enabled = currentRules().getBoolean(happy_mode.RULE);
        if (selection.isEnabled(SpecialWindowTree.ROOT_ID) != enabled) {
            selection.setRootEnabled(enabled);
            rebuildNodes();
        }
    }

    /**
     * 【未来游戏效果接入点】Selection 先处理全前置 AND 与级联关闭，再接入真实效果。
     * 原点直接修改创建世界草稿中的 Happy_mode（server=null），不会修改已进入的世界。
     * 若未来子节点也有实际效果/存档，应比较变更前后的 enabledIds，把所有被级联关闭的
     * 节点一并撤销；不能仅根据本次点击的 id 发放/移除效果。现在子节点只保存草稿选择。
     */
    private void toggleNode(String id) {
        synchronizeRoot();
        if (selection.toggle(id)) {
            if (SpecialWindowTree.ROOT_ID.equals(id)) {
                currentRules().getRule(happy_mode.RULE)
                        .set(selection.isEnabled(SpecialWindowTree.ROOT_ID), null);
            }
            rebuildNodes();
        }
        // 无法开启时保持蓝色，描述框会列出缺少的前置；只增加一次噪场扰动。
        toggledAt = Util.getMillis();
    }

    private void rebuildNodes() {
        float time = (Util.getMillis() - openedAt) / 1000.0F;
        Map<String, Float> activation = new LinkedHashMap<>();
        List<Node> nodes = new ArrayList<>();
        for (NodeSpec node : TREE.nodes()) {
            boolean enabled = selection.isEnabled(node.id());
            NoiseTransition transition = nodeTransitions.computeIfAbsent(node.id(),
                    ignored -> new NoiseTransition(enabled ? 1 : 0, NODE_TRANSITION_SECONDS));
            transition.target(enabled ? 1 : 0, time);
            float value = transition.value(time);
            activation.put(node.id(), value);
            nodes.add(new Node(node.x(), node.y(), node.visualSeed(),
                    SpecialWindowTree.ROOT_ID.equals(node.id()), enabled, value));
        }
        renderedNodes = List.copyOf(nodes);
        renderedLinks = buildBranches(activation);
    }

    private NodeSpec nodeAt(double mouseX, double mouseY) {
        float x = worldX(mouseX);
        float y = worldY(mouseY);
        NodeSpec nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (NodeSpec node : TREE.nodes()) {
            double distance = Math.hypot(x - node.x(), y - node.y());
            float radius = SpecialWindowTree.ROOT_ID.equals(node.id()) ? ROOT_HIT_RADIUS : NODE_HIT_RADIUS;
            if (distance <= radius && distance < nearestDistance) {
                nearest = node;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /**
     * 【描述框调试】TOOLTIP_MAX_WIDTH 控制自动换行；padding/margin 控制边距。
     * 底层与黑白碎边由 NoiseTooltipRenderer 绘制，展开/收拢速度调上面的 TOOLTIP_*_SECONDS。
     * 字体保持白色；文字先按中心裁剪显露，再完全可读，不使用原版紫色 tooltip。
     * 修改内容用 NodeSpec 的 titleKey / descriptionKey 与中英文 lang JSON。
     */
    private void renderNodeDescription(GuiGraphics graphics, NodeSpec node, int mouseX, int mouseY,
                                       float progress, float time) {
        int maxWidth = Math.max(24, Math.min(TOOLTIP_MAX_WIDTH,
                width - 2 * (TOOLTIP_MARGIN + TOOLTIP_PADDING)));
        List<FormattedCharSequence> lines = new ArrayList<>();
        addDescriptionLines(lines, Component.translatable(node.titleKey()), maxWidth);
        addDescriptionLines(lines, Component.translatable(node.descriptionKey()), maxWidth);
        boolean enabled = selection.isEnabled(node.id());
        boolean available = selection.canEnable(node.id());
        addDescriptionLines(lines, Component.translatable("white_lily.special_window.tooltip."
                + (enabled ? "enabled" : available ? "disabled" : "locked")), maxWidth);
        if (!node.prerequisites().isEmpty()) {
            addDescriptionLines(lines, Component.translatable("white_lily.special_window.tooltip.requires_all"), maxWidth);
            for (String prerequisite : node.prerequisites()) {
                String marker = selection.isEnabled(prerequisite) ? "+ " : "- ";
                addDescriptionLines(lines, Component.literal(marker)
                        .append(Component.translatable(TREE.node(prerequisite).titleKey())), maxWidth);
            }
        }
        addDescriptionLines(lines, Component.translatable("white_lily.special_window.tooltip."
                + (enabled ? "click_disable" : available ? "click_enable" : "blocked")), maxWidth);
        // 原版最小GUI也限制高度，避免低分辨率/长翻译把整个提示框挤出屏幕。
        int maxLines = Math.max(1, (height - 2 * (TOOLTIP_MARGIN + TOOLTIP_PADDING)) / font.lineHeight);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
        }
        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(24);
        int boxWidth = contentWidth + TOOLTIP_PADDING * 2;
        int boxHeight = lines.size() * font.lineHeight + TOOLTIP_PADDING * 2;
        int left = mouseX + 12;
        if (left + boxWidth > width - TOOLTIP_MARGIN) left = mouseX - 12 - boxWidth;
        left = Mth.clamp(left, TOOLTIP_MARGIN, Math.max(TOOLTIP_MARGIN, width - boxWidth - TOOLTIP_MARGIN));
        int top = Mth.clamp(mouseY + 12, TOOLTIP_MARGIN,
                Math.max(TOOLTIP_MARGIN, height - boxHeight - TOOLTIP_MARGIN));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        NoiseTooltipRenderer.render(graphics, left, top, boxWidth, boxHeight, time, progress);
        float textReveal = ease(Mth.clamp((progress - 0.60F) / 0.40F, 0, 1));
        if (textReveal > 0.02F) {
            int visibleWidth = Math.max(1, (int) (contentWidth * textReveal));
            int visibleHeight = Math.max(1, (int) (lines.size() * font.lineHeight * textReveal));
            int cx = left + boxWidth / 2, cy = top + boxHeight / 2;
            graphics.enableScissor(cx - visibleWidth / 2, cy - visibleHeight / 2,
                    cx + (visibleWidth + 1) / 2, cy + (visibleHeight + 1) / 2);
            int textColor = ((int) (textReveal * 255) << 24) | 0xFFFFFF;
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), left + TOOLTIP_PADDING,
                        top + TOOLTIP_PADDING + i * font.lineHeight, textColor, false);
            }
            graphics.disableScissor();
        }
        graphics.pose().popPose();
    }

    private void addDescriptionLines(List<FormattedCharSequence> lines, Component component, int maxWidth) {
        lines.addAll(font.split(component, maxWidth));
    }

    private float worldX(double x) { return ((float) x - width / 2.0F - panX) / zoom; }
    private float worldY(double y) { return ((float) y - height / 2.0F - panY) / zoom; }

    static int color(int rgb, float opacity) {
        int alpha = (int) (Mth.clamp(opacity, 0, 1) * 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static float ease(float t) { return t * t * (3.0F - 2.0F * t); }

    private static List<Link> buildBranches(Map<String, Float> activation) {
        List<Link> result = new ArrayList<>();
        int routeIndex = 0;
        // 【连线调试】每个 Connection 代表一条真实前置关系；bends 只改变路径外观。
        // 从前置节点中心出发，经中转点，到子节点中心。不要再手画一条不参与依赖的线。
        for (Connection connection : TREE.connections()) {
            NodeSpec from = TREE.node(connection.fromId());
            NodeSpec to = TREE.node(connection.toId());
            // 两端都完全点亮才得到完全稳定的白线；关闭任一端便随其约一秒过渡散回粗噪带。
            // 使用视觉激活进度的较小值，而不是逻辑开关直接跳变，快速反向点击也连续。
            float stability = Math.min(activation.get(connection.fromId()), activation.get(connection.toId()));
            Point previous = new Point(from.x(), from.y());
            List<Point> path = new ArrayList<>(connection.bends());
            path.add(new Point(to.x(), to.y()));
            for (Point next : path) {
                if (Math.hypot(next.x() - previous.x(), next.y() - previous.y()) > 0.01) {
                    // Link.depth 这里只作为噪声种子的变化量，不决定解锁顺序。
                    result.add(new Link(previous.x(), previous.y(), next.x(), next.y(), routeIndex, stability));
                }
                previous = next;
            }
            routeIndex++;
        }
        return List.copyOf(result);
    }
}
