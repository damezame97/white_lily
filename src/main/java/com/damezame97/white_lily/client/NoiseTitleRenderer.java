package com.damezame97.white_lily.client;

import com.damezame97.white_lily.white_lily;
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

import java.io.IOException;

/**
 * 主菜单专用黑白噪域背景；只画背景，不持有菜单概率、访问状态或任何输入逻辑。
 * 调用位置必须在标题/按钮之前。Opacity 只让灰白噪粒渐入，黑底始终完全不透明，
 * 因此即使 opacity=0，也不会让下面的全景图穿过黑色孔洞。
 * 主要艺术参数见独立的 noise_title.fsh，不复用描述文件 shader，避免改变旧描述框。
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoiseTitleRenderer {
    private static ShaderInstance titleShader;

    // 降级调试：小粒数量上限、白碎边片数。没有逐帧扫描屏幕像素的 CPU 循环。
    private static final int FALLBACK_MAX_PARTICLES = 2600;
    private static final int FALLBACK_VOID_COUNT = 9;
    private static final int FALLBACK_EDGE_FRAGMENTS = 45;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        titleShader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(white_lily.MODID, "noise_title"), DefaultVertexFormat.POSITION_TEX),
                    shader -> titleShader = shader);
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("White Lily: title noise shader unavailable; using monochrome fragments", error);
        }
    }

    /** time 为单调动画秒数；opacity 控制噪粒亮度，底层黑色遮罩始终完整覆盖屏幕。 */
    public static void render(GuiGraphics g, int width, int height, float time, float opacity) {
        if (width <= 0 || height <= 0) return;
        opacity = Mth.clamp(opacity, 0, 1);
        ShaderInstance shader = titleShader;
        if (shader == null) {
            fallback(g, width, height, time, opacity);
            return;
        }
        g.flush();
        shader.safeGetUniform("NoiseTime").set(time);
        shader.safeGetUniform("FieldSize").set((float) width, (float) height);
        shader.safeGetUniform("Opacity").set(opacity);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(() -> shader);
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(pose, 0, 0, 0).uv(0, 0).endVertex();
        b.vertex(pose, 0, height, 0).uv(0, 1).endVertex();
        b.vertex(pose, width, height, 0).uv(1, 1).endVertex();
        b.vertex(pose, width, 0, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** 资源包损坏时仍保留不透明黑底、局部混乱灰白粒与不规则碎边孔洞。 */
    @SuppressWarnings("deprecation")
    private static void fallback(GuiGraphics g, int width, int height, float time, float opacity) {
        g.drawManaged(() -> {
            g.fill(0, 0, width, height, 0xFF000000);
            if (opacity <= 0) return;
            int count = Math.min(FALLBACK_MAX_PARTICLES, Math.max(600, width * height / 42));
            for (int i = 0; i < count; i++) {
                int seed = i * 937 + 171;
                float rx = random(seed), ry = random(seed + 53), rz = random(seed + 127);
                float phase = rz * Mth.TWO_PI;
                int x = (int) (rx * width + Mth.sin(time * (1.7F + rz * 6.3F) + phase) * 1.8F);
                int y = (int) (ry * height + Mth.sin(time * (2.1F + rx * 7.2F) - phase) * 1.8F);
                float life = 0.5F + 0.5F * Mth.sin(time * (8 + ry * 23) + phase);
                float quiet = 0.45F + 0.55F * Math.min(1,
                        (float) Math.hypot((rx - 0.5F) * 2.0F, (ry - 0.56F) * 1.8F));
                int alpha = (int) (opacity * quiet * (0.045F + life * life * (0.16F + rz * 0.51F)) * 255);
                int size = rz > 0.96F ? 2 : 1;
                g.fill(x, y, x + size, y + size, alpha << 24 | 0xFFFFFF);
            }
            // 黑孔洞分别以不同速率重排，周围的白碎边不连成完整矩形框。
            for (int i = 0; i < FALLBACK_VOID_COUNT; i++) {
                int seed = i * 7477 + 9127;
                int phase = (int) (time * (1.7F + random(seed + 11) * 2.4F) + random(seed) * 31);
                int cx = (int) (random(seed + phase * 31) * width);
                int cy = (int) (random(seed + phase * 61 + 19) * height);
                int hw = 8 + (int) (random(seed + 13) * width * 0.12F);
                int hh = 7 + (int) (random(seed + 47) * height * 0.15F);
                g.fill(cx - hw, cy - hh, cx + hw, cy + hh, 0xFF000000);
                for (int fragment = 0; fragment < FALLBACK_EDGE_FRAGMENTS; fragment++) {
                    int fs = seed + fragment * 719 + (int) (time * 23) * 37;
                    float along = random(fs) * 2 - 1;
                    int side = fragment & 3;
                    int x = side < 2 ? cx + (int) (along * hw) : cx + (side == 2 ? -hw : hw);
                    int y = side < 2 ? cy + (side == 0 ? -hh : hh) : cy + (int) (along * hh);
                    x += (int) (random(fs + 29) * 7) - 3;
                    y += (int) (random(fs + 71) * 7) - 3;
                    int alpha = (int) (opacity * (0.06F + random(fs + 131) * 0.50F) * 255);
                    g.fill(x, y, x + 1, y + 1, alpha << 24 | 0xFFFFFF);
                }
            }
        });
    }

    private static float random(int n) {
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    private NoiseTitleRenderer() { }
}
