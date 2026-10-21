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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * 黑白噪声描述文件的底层与边缘；正文由 Screen 在其展开后绘制。
 * 【视觉调试】noise_tooltip.fsh 控制碎边、颗粒与中心扩散；Screen 的 TOOLTIP_* 控制文字和时长。
 * reveal 从0到1向外蔓延，反向则向中心坍缩。这里不读鼠标，也不处理节点逻辑。
 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoiseTooltipRenderer {
    private static ShaderInstance documentShader;

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        documentShader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(white_lily.MODID, "noise_tooltip"), DefaultVertexFormat.POSITION_TEX),
                    shader -> documentShader = shader);
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("White Lily: noise document shader unavailable; using fragments", error);
        }
    }

    public static void render(GuiGraphics g, int x, int y, int width, int height,
                              float time, float reveal) {
        if (reveal <= 0 || width <= 0 || height <= 0) return;
        if (documentShader == null) {
            fallback(g, x, y, width, height, time, reveal);
            return;
        }
        g.flush();
        documentShader.safeGetUniform("NoiseTime").set(time);
        documentShader.safeGetUniform("FieldSize").set((float) width, (float) height);
        documentShader.safeGetUniform("Reveal").set(reveal);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(() -> documentShader);
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.vertex(pose, x, y, 0).uv(0, 0).endVertex();
        b.vertex(pose, x, y + height, 0).uv(0, 1).endVertex();
        b.vertex(pose, x + width, y + height, 0).uv(1, 1).endVertex();
        b.vertex(pose, x + width, y, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** 无 shader 时依然使用中心扩展的黑底和碎白点，不能退回旧的完整白矩形边框。 */
    @SuppressWarnings("deprecation")
    private static void fallback(GuiGraphics g, int x, int y, int width, int height,
                                 float time, float reveal) {
        float spread = reveal * reveal * (3 - 2 * reveal);
        int cx = x + width / 2, cy = y + height / 2;
        int hw = Math.max(1, (int) ((width / 2 - 5) * spread));
        int hh = Math.max(1, (int) ((height / 2 - 5) * spread));
        int frame = (int) (time * 24);
        g.drawManaged(() -> {
            g.fill(cx - hw, cy - hh, cx + hw, cy + hh, 0xFF000000);
            for (int i = 0; i < 170; i++) {
                int h = hash(i * 937 + frame * 19);
                boolean horizontal = (i & 1) == 0;
                int a = ((h >>> 4) & 1023) - 512;
                int px = horizontal ? cx + a * hw / 512 : cx + ((i & 2) == 0 ? hw : -hw);
                int py = horizontal ? cy + ((i & 2) == 0 ? hh : -hh) : cy + a * hh / 512;
                px += ((h >>> 15) & 7) - 3;
                py += ((h >>> 20) & 7) - 3;
                int size = 1 + ((h >>> 24) & 1);
                int alpha = (int) ((0.18F + ((h >>> 9) & 63) / 90.0F) * reveal * 255);
                g.fill(px, py, px + size, py + size, alpha << 24 | 0xFFFFFF);
            }
        });
    }

    private static int hash(int n) {
        n ^= n >>> 16;
        n *= 0x7feb352d;
        n ^= n >>> 15;
        return n * 0x846ca68b;
    }

    private NoiseTooltipRenderer() { }
}
