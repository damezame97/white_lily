package com.damezame97.white_lily.client;

import com.damezame97.white_lily.white_lily;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/** Local, bounded shader quads: branch cost scales with their visible area, not the entire screen. */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoiseTreeRenderer {
    public record Link(float x1, float y1, float x2, float y2, int depth, float stability) {
        /** 兼容旧调用：0 为粗白噪带，1 为真正静止的细白线。 */
        public Link(float x1, float y1, float x2, float y2, int depth) {
            this(x1, y1, x2, y2, depth, 0.0F);
        }
    }
    public record Node(float x, float y, int seed, boolean origin, boolean enabled, float activation) {
        /** 兼容旧调用：没有过渡进度时直接显示逻辑目标的端点颜色。 */
        public Node(float x, float y, int seed, boolean origin, boolean enabled) {
            this(x, y, seed, origin, enabled, enabled ? 1.0F : 0.0F);
        }
        /** 兼容旧的预览调用：未传状态的节点默认呈蓝色噪团。 */
        public Node(float x, float y, int seed, boolean origin) {
            this(x, y, seed, origin, false);
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance treeShader;
    // 几何调试：节点光晕和乱码带均使用世界坐标，随屏幕的缩放一起变化。
    private static final float LINK_PAD = 18.0F;
    private static final float LINK_HALF_WIDTH = 28.0F;
    private static final float NODE_HALF_SIZE = 62.0F;
    // shader 不可用时的配色；开启态压低蓝通道，保证看到红色而非紫色。
    private static final int BLUE_HALO = 0x1269FE;
    private static final int BLUE_MASS = 0x126AFF;
    private static final int BLUE_CHIP = 0x67CFFF;
    private static final int BLUE_EXPOSURE = 0xD4F7FF;
    private static final int RED_HALO = 0xF61209;
    private static final int RED_MASS = 0xFF2114;
    private static final int RED_CHIP = 0xFF6A32;
    private static final int RED_EXPOSURE = 0xFFE6CE;
    // fallback 动态调试：各向同性小方粒数量、模糊小块重排频率、光晕透明度。
    // activation/stability 由 Screen 在约 1 秒内推进，renderer 只按当前进度绘制，不另起计时器。
    // 粒子数135->420、模糊小块20->90；单粒更小，避免大块稀疏地漂浮。
    private static final int FALLBACK_FRAGMENT_COUNT = 420;
    private static final int FALLBACK_BLOCK_COUNT = 90;
    private static final float FALLBACK_BLOCK_RATE = 3.4F;
    private static final float FALLBACK_HALO_OPACITY = 0.018F;
    private static final float FALLBACK_LOOSE_HALF_WIDTH = 16.0F;
    private static final float FALLBACK_STABLE_HALF_WIDTH = 0.60F;
    private static final float FALLBACK_STABLE_HALO_HALF_WIDTH = 2.7F;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        treeShader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(white_lily.MODID, "noise_tree"),
                            DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> treeShader = loaded);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("White Lily: noise tree shader unavailable; using fragment fallback", exception);
        }
    }

    /**
     * The caller has already translated and scaled the pose into tree coordinates.
     * Both the geometry and mouse coordinates are in that same world coordinate space.
     * 每个节点的 activation 决定蓝红分粒置换及爆散重组；enabled 仅保留逻辑目标。
     * 每条连线的 stability 决定从粗白噪带到静态细白线的收拢进度。
     * 最后一个 boolean 仅为兼容旧调用保留，不再覆盖根节点或其他节点的状态。
     */
    public static void render(GuiGraphics g, List<Link> links, List<Node> nodes, float time,
                              float mouseWorldX, float mouseWorldY, float activity,
                              float opacity, boolean ignoredLegacyOriginState) {
        if (opacity <= 0.0F || links.isEmpty() && nodes.isEmpty()) return;
        activity = Mth.clamp(activity, 0.0F, 1.0F);
        opacity = Mth.clamp(opacity, 0.0F, 1.0F);
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f pose = g.pose().last().pose();
        if (treeShader == null) {
            renderFallback(pose, links, nodes, time, mouseWorldX, mouseWorldY, activity, opacity);
        } else {
            set(treeShader, "NoiseTime", time);
            set(treeShader, "Activity", activity);
            set(treeShader, "Opacity", opacity);
            RenderSystem.setShader(() -> treeShader);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int index = 0;
            for (Link link : links) {
                float dx = link.x2 - link.x1;
                float dy = link.y2 - link.y1;
                float length = Mth.sqrt(dx * dx + dy * dy);
                if (length < 0.01F) continue;
                float alongX = dx / length;
                float alongY = dy / length;
                float hover = proximity(mouseWorldX, mouseWorldY, link, 54.0F);
                int seed = (index++ * 73 + link.depth * 37 + (int) (link.x1 * 3 + link.y1 * 7)) & 255;
                int encodedLength = Mth.clamp(Math.round(length * 0.25F), 1, 255);
                int encodedHover = Math.round(hover * 255);
                int encodedStability = Math.round(Mth.clamp(link.stability, 0, 1) * 127);
                vertex(b, pose, link, alongX, alongY, -LINK_PAD, -LINK_HALF_WIDTH,
                        seed, encodedLength, encodedStability, encodedHover);
                vertex(b, pose, link, alongX, alongY, -LINK_PAD, LINK_HALF_WIDTH,
                        seed, encodedLength, encodedStability, encodedHover);
                vertex(b, pose, link, alongX, alongY, length + LINK_PAD, LINK_HALF_WIDTH,
                        seed, encodedLength, encodedStability, encodedHover);
                vertex(b, pose, link, alongX, alongY, length + LINK_PAD, -LINK_HALF_WIDTH,
                        seed, encodedLength, encodedStability, encodedHover);
            }
            for (Node node : nodes) {
                float hover = 1.0F - Mth.clamp((float) Math.hypot(mouseWorldX - node.x,
                        mouseWorldY - node.y) / 54.0F, 0, 1);
                int h = Math.round(hover * 255);
                nodeVertex(b, pose, node, -NODE_HALF_SIZE, -NODE_HALF_SIZE, h);
                nodeVertex(b, pose, node, -NODE_HALF_SIZE, NODE_HALF_SIZE, h);
                nodeVertex(b, pose, node, NODE_HALF_SIZE, NODE_HALF_SIZE, h);
                nodeVertex(b, pose, node, NODE_HALF_SIZE, -NODE_HALF_SIZE, h);
            }
            BufferUploader.drawWithShader(b.end());
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder b, Matrix4f pose, Link link, float ax, float ay,
                               float u, float v, int r, int green, int blue, int a) {
        // 连接线顶点数据：UV=沿线/垂线世界像素；r=seed，g=长度/4，b=round(stability*127)，a=悬停。
        // b 最多 127，与节点固定 b=255 区分。稳定值 127 对应 shader 的完全静态分支。
        b.vertex(pose, link.x1 + ax * u - ay * v, link.y1 + ay * u + ax * v, 0)
                .uv(u, v).color(r, green, blue, a).endVertex();
    }

    private static void nodeVertex(BufferBuilder b, Matrix4f pose, Node n, float u, float v, int hover) {
        // 节点顶点数据：UV=相对中心的世界像素；r=seed，b=255，a=悬停强度。
        // g 的 byte 位 7 表示根节点；低 7 位为 round(activation*127)，连续控制变色和爆散。
        int nodeFlags = (n.origin ? 128 : 0) + Math.round(Mth.clamp(n.activation, 0, 1) * 127);
        b.vertex(pose, n.x + u, n.y + v, 0).uv(u, v)
                .color(n.seed & 255, nodeFlags, 255, hover).endVertex();
    }

    private static float proximity(float x, float y, Link l, float radius) {
        float dx = l.x2 - l.x1;
        float dy = l.y2 - l.y1;
        float t = Mth.clamp(((x - l.x1) * dx + (y - l.y1) * dy)
                / Math.max(0.01F, dx * dx + dy * dy), 0.0F, 1.0F);
        return 1.0F - Mth.clamp((float) Math.hypot(x - l.x1 - dx * t,
                y - l.y1 - dy * t) / radius, 0, 1);
    }

    private static void set(ShaderInstance shader, String name, float value) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    /** shader 资源失效时仍保留白噪带收拢、节点模糊块体与分粒变色，不使页面不可用。 */
    private static void renderFallback(Matrix4f pose, List<Link> links, List<Node> nodes,
                                       float time, float mx, float my, float activity,
                                       float opacity) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int index = 0;
        for (Link link : links) {
            float dx = link.x2 - link.x1;
            float dy = link.y2 - link.y1;
            float length = (float) Math.hypot(dx, dy);
            if (length < 0.01F) continue;
            float ax = dx / length;
            float ay = dy / length;
            int seed = index++ * 937 + link.depth * 347;
            float stable = Mth.clamp(link.stability, 0, 1);
            if (stable >= 0.999F) {
                // 完全稳定后不读取 time、hover 或 activity：白线的几何和亮度恒定。
                fallbackStableLine(b, pose, link, ax, ay, length, opacity);
                continue;
            }
            float loose = 1 - stable;
            float width = Mth.lerp(stable, FALLBACK_LOOSE_HALF_WIDTH, FALLBACK_STABLE_HALF_WIDTH);
            float hover = proximity(mx, my, link, 54) * loose;
            for (float at = -5 * loose; at < length + 5 * loose; at += 3.2F) {
                int column = (int) (at / 3.2F);
                for (int row = -6; row <= 6; row++) {
                    // 每粒独立双轴频率/相位；没有共用的逐列位移，也没有沿线流动。
                    int ps = seed + column * 971 + row * 373;
                    float rx = noise(ps + 19), ry = noise(ps + 61), rz = noise(ps + 97);
                    float x = at + Mth.sin(time * (1.7F + rx * 7.3F) + ry * Mth.TWO_PI)
                            * loose * 0.9F;
                    float y = row / 6.0F * width
                            + Mth.sin(time * (2.1F + rz * 8.1F) + rx * Mth.TWO_PI)
                            * loose * (0.8F + hover * 0.3F);
                    float n = 0.5F + 0.5F * Mth.sin(time * (7 + rz * 21) + ry * Mth.TWO_PI);
                    float edge = 1 - Math.abs(row) / 7.0F;
                    float size = 0.65F + rz * 1.1F;
                    rectAlong(b, pose, link, ax, ay, x, y, x + size, y + size,
                            0xFFFFFF, opacity * loose * edge * (0.07F + n * n * 0.72F));
                }
            }
            fallbackStableLine(b, pose, link, ax, ay, length, opacity * stable * stable);
        }
        for (Node node : nodes) {
            float hover = 1 - Mth.clamp((float) Math.hypot(mx - node.x, my - node.y) / 54, 0, 1);
            float activation = Mth.clamp(node.activation, 0, 1);
            float burst = 4 * activation * (1 - activation);
            float exposure = Mth.lerp(activation, node.origin ? 0.85F : 0.90F, node.origin ? 1.3F : 1.2F)
                    + hover * 0.5F + activity * 0.2F;
            for (int halo = 0; halo < 18; halo++) {
                int hs = node.seed * 97 + halo * 439;
                float angle = noise(hs) * Mth.TWO_PI;
                float radius = 13 + noise(hs + 51) * 13 + burst * 7;
                float offset = 7 + burst * 6;
                float x = node.x + Mth.cos(angle) * offset
                        + Mth.sin(time * (0.7F + noise(hs + 77) * 2.3F) + angle) * 1.2F;
                float y = node.y + Mth.sin(angle) * offset
                        + Mth.sin(time * (0.9F + noise(hs + 113) * 2.9F) - angle) * 1.2F;
                rect(b, pose, x - radius, y - radius, x + radius, y + radius,
                        noise(hs + 13) < activation ? RED_HALO : BLUE_HALO,
                        opacity * exposure * FALLBACK_HALO_OPACITY);
            }
            // 模糊小块约 0.3 秒局部重排；尺寸3.5..8.5缩到0.9..2.4，增加数量保持细密。
            int blockPhase = (int) (time * FALLBACK_BLOCK_RATE);
            for (int block = 0; block < FALLBACK_BLOCK_COUNT; block++) {
                int bs = node.seed * 191 + block * 593;
                float x = (noise(bs + blockPhase * 17) - 0.5F) * (node.origin ? 40 : 32)
                        * (1 + burst * 0.72F);
                float y = (noise(bs + blockPhase * 41 + 53) - 0.5F) * (node.origin ? 40 : 32)
                        * (1 + burst * 0.72F);
                float size = 0.9F + noise(bs + 97) * 1.5F;
                float falloff = Math.max(0, 1 - (float) Math.hypot(x, y) / (35 + burst * 18));
                int color = noise(bs + 73) < activation ? RED_MASS : BLUE_MASS;
                for (int blur = 3; blur >= 0; blur--) {
                    float extent = size + blur * 0.55F;
                    rect(b, pose, node.x + x - extent, node.y + y - extent,
                            node.x + x + extent, node.y + y + extent, color,
                            opacity * exposure * falloff * (blur == 0 ? 0.12F : 0.035F));
                }
            }
            for (int i = 0; i < FALLBACK_FRAGMENT_COUNT; i++) {
                int seed = node.seed * 79 + i * 347;
                float angle = noise(seed) * Mth.TWO_PI;
                float radius = (float) Math.sqrt(noise(seed + 37)) * (node.origin ? 29 : 25)
                        * (1 + burst * 0.75F);
                float x = Mth.cos(angle) * radius
                        + Mth.sin(time * (1.7F + noise(seed + 13) * 7.3F) + angle) * (1.8F + burst * 4);
                float y = Mth.sin(angle) * radius
                        + Mth.sin(time * (2.1F + noise(seed + 53) * 8.1F) - angle) * (1.8F + burst * 4);
                float falloff = Math.max(0, 1 - radius / (48 + burst * 22));
                // 等宽方粒，蓝红由每粒自己的阈值置换，不把整个节点静态混成紫色。
                float size = 0.28F + noise(seed + 31) * (i % 7 == 0 ? 0.85F : 0.60F);
                boolean red = noise(seed + 71) < activation;
                rect(b, pose, node.x + x - size / 2, node.y + y - size / 2,
                        node.x + x + size / 2, node.y + y + size / 2,
                        red
                                ? i % 19 == 0 ? RED_EXPOSURE : i % 3 == 0 ? RED_CHIP : RED_MASS
                                : i % 19 == 0 ? BLUE_EXPOSURE : i % 3 == 0 ? BLUE_CHIP : BLUE_MASS,
                        opacity * falloff * exposure * (i % 19 == 0 ? 0.75F : 0.23F));
            }
        }
        BufferUploader.drawWithShader(b.end());
    }

    /** 纯距离分层的细白光晕，最大半宽2.7世界像素；没有任何时间或交互输入。 */
    private static void fallbackStableLine(BufferBuilder b, Matrix4f pose, Link link,
                                           float ax, float ay, float length, float opacity) {
        rectAlong(b, pose, link, ax, ay, 0, -FALLBACK_STABLE_HALO_HALF_WIDTH,
                length, FALLBACK_STABLE_HALO_HALF_WIDTH, 0xFFFFFF, opacity * 0.022F);
        rectAlong(b, pose, link, ax, ay, 0, -1.85F, length, 1.85F, 0xFFFFFF, opacity * 0.038F);
        rectAlong(b, pose, link, ax, ay, 0, -1.15F, length, 1.15F, 0xFFFFFF, opacity * 0.075F);
        rectAlong(b, pose, link, ax, ay, 0, -FALLBACK_STABLE_HALF_WIDTH,
                length, FALLBACK_STABLE_HALF_WIDTH, 0xFFFFFF, opacity * 0.96F);
    }

    private static void rectAlong(BufferBuilder b, Matrix4f pose, Link l, float ax, float ay,
                                  float x1, float y1, float x2, float y2, int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        fallbackVertex(b, pose, l.x1 + ax * x1 - ay * y1, l.y1 + ay * x1 + ax * y1, rgb, a);
        fallbackVertex(b, pose, l.x1 + ax * x1 - ay * y2, l.y1 + ay * x1 + ax * y2, rgb, a);
        fallbackVertex(b, pose, l.x1 + ax * x2 - ay * y2, l.y1 + ay * x2 + ax * y2, rgb, a);
        fallbackVertex(b, pose, l.x1 + ax * x2 - ay * y1, l.y1 + ay * x2 + ax * y1, rgb, a);
    }

    private static void rect(BufferBuilder b, Matrix4f pose, float x1, float y1,
                             float x2, float y2, int rgb, float opacity) {
        int a = (int) (Mth.clamp(opacity, 0, 1) * 255);
        fallbackVertex(b, pose, x1, y1, rgb, a);
        fallbackVertex(b, pose, x1, y2, rgb, a);
        fallbackVertex(b, pose, x2, y2, rgb, a);
        fallbackVertex(b, pose, x2, y1, rgb, a);
    }

    private static void fallbackVertex(BufferBuilder b, Matrix4f pose, float x, float y, int rgb, int a) {
        b.vertex(pose, x, y, 0).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, a).endVertex();
    }

    private static float noise(int n) {
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    private NoiseTreeRenderer() { }
}
