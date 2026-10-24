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

/** Animated, texture-free blue noise used by the portal and its interior. */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoiseFieldRenderer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance fieldShader;
    private static final float[] TRAIL_UNIFORMS = new float[NoiseCursorTrail.MAX_SAMPLES * 4];

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event)
    {
        // Resource reloads replace the instance. Do not retain the now-closed shader.
        fieldShader = null;
        try
        {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            new ResourceLocation(white_lily.MODID, "noise_field"), DefaultVertexFormat.POSITION_TEX),
                    loaded -> fieldShader = loaded);
        }
        catch (IOException | RuntimeException exception)
        {
            LOGGER.warn("White Lily: noise field shader unavailable; using the gradient fallback", exception);
        }
    }

    /**
     * @param time elapsed animation time in seconds
     * @param mouseX absolute mouse position in GUI coordinates (same coordinates as x/y)
     * @param mouseY absolute mouse position in GUI coordinates
     * @param activity interaction intensity in [0, 1]
     * @param opacity fade amount in [0, 1]
     * @param portal true for the irregular corner fragment, false for the full field
     */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              float time, float mouseX, float mouseY, float activity,
                              float opacity, boolean portal)
    {
        render(graphics, x, y, width, height, time, mouseX, mouseY, activity,
                opacity, portal, List.of());
    }

    /**
     * trail 使用绝对 GUI 坐标；旧重载保留入口原本的纯蓝表现。
     * 可调：半径/持续时间在 NoiseCursorTrail；红色软块密度在 noise_field.fsh
     * 的 exposureBlocks（density=0.72），只控制局部碎块，不改变背景底色。
     */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              float time, float mouseX, float mouseY, float activity,
                              float opacity, boolean portal, List<NoiseCursorTrail.Sample> trail)
    {
        if (width <= 0 || height <= 0 || opacity <= 0.0F)
        {
            return;
        }

        activity = Mth.clamp(activity, 0.0F, 1.0F);
        opacity = Mth.clamp(opacity, 0.0F, 1.0F);
        ShaderInstance shader = fieldShader;
        if (shader == null)
        {
            renderFallback(graphics, x, y, width, height, time, activity, opacity, portal, trail);
            return;
        }

        // GuiGraphics batches its own draw calls. Flush before changing the shader.
        graphics.flush();
        set(shader, "NoiseTime", time);
        set(shader, "FieldSize", width, height);
        set(shader, "Cursor", (mouseX - x) / width, (mouseY - y) / height);
        set(shader, "Activity", activity);
        set(shader, "Opacity", opacity);
        set(shader, "Portal", portal ? 1.0F : 0.0F);
        uploadTrail(shader, trail, x, y, width, height);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(pose, x, y, 0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(pose, x, y + height, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + width, y + height, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(pose, x + width, y, 0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void set(ShaderInstance shader, String name, float value)
    {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null)
        {
            uniform.set(value);
        }
    }

    private static void set(ShaderInstance shader, String name, float x, float y)
    {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null)
        {
            uniform.set(x, y);
        }
    }

    private static void uploadTrail(ShaderInstance shader, List<NoiseCursorTrail.Sample> trail,
                                    int x, int y, int width, int height)
    {
        int count = trail == null ? 0 : Math.min(trail.size(), NoiseCursorTrail.MAX_SAMPLES);
        int start = trail == null ? 0 : trail.size() - count;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < NoiseCursorTrail.MAX_SAMPLES; i++)
        {
            int at = i * 4;
            if (i < count)
            {
                NoiseCursorTrail.Sample sample = trail.get(start + i);
                float px = (sample.x() - x) / width;
                float py = (sample.y() - y) / height;
                TRAIL_UNIFORMS[at] = px;
                TRAIL_UNIFORMS[at + 1] = py;
                TRAIL_UNIFORMS[at + 2] = Mth.clamp(sample.strength(), 0, 1);
                TRAIL_UNIFORMS[at + 3] = sample.connected() && i > 0 ? 1 : 0;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            else
            {
                TRAIL_UNIFORMS[at] = TRAIL_UNIFORMS[at + 1] = 0;
                TRAIL_UNIFORMS[at + 2] = TRAIL_UNIFORMS[at + 3] = 0;
            }
        }
        // 1.20.1 的 JSON float/count>4 上传为 glUniform1fv，所以显式打包标量数组。
        Uniform points = shader.getUniform("TrailPoints");
        if (points != null) points.set(TRAIL_UNIFORMS);
        Uniform countUniform = shader.getUniform("TrailCount");
        if (countUniform != null) countUniform.set(count);
        set(shader, "TrailRadius", NoiseCursorTrail.RADIUS_GUI_PIXELS);
        Uniform bounds = shader.getUniform("TrailBounds");
        if (bounds != null)
        {
            float rx = NoiseCursorTrail.RADIUS_GUI_PIXELS / width;
            float ry = NoiseCursorTrail.RADIUS_GUI_PIXELS / height;
            if (count == 0) bounds.set(0.0F, 0.0F, 0.0F, 0.0F);
            else bounds.set(minX - rx, minY - ry, maxX + rx, maxY + ry);
        }
    }

    /** Resource-pack shader errors must not make the world creation screen unusable. */
    @SuppressWarnings("deprecation")
    private static void renderFallback(GuiGraphics graphics, int x, int y, int width, int height,
                                       float time, float activity, float opacity, boolean portal,
                                       List<NoiseCursorTrail.Sample> trail)
    {
        int alpha = (int) (opacity * 255.0F);
        int frame = (int) (time * (10.0F + activity * 12.0F));
        int columnSize = Math.max(2, width / 23);
        graphics.drawManaged(() -> {
            if (!portal)
            {
                graphics.fillGradient(x, y, x + width, y + height,
                        alpha << 24 | 0x03081E, alpha << 24 | 0x040A22);
            }
            else
            {
                int previousTop = height;
                for (int column = 0; column < width; column += columnSize)
                {
                    int top = fallbackTop(column, columnSize, width, height, frame, activity);
                    int end = x + Math.min(width, column + columnSize);
                    graphics.fill(x + column, y + top, end, y + height, alpha << 24 | 0x040A22);
                    int rim = (int) (alpha * (0.60F + activity * 0.35F)) << 24 | 0x216BC4;
                    graphics.fill(x + column, y + top, end, y + top + 1, rim);
                    if (column > 0)
                    {
                        graphics.fill(x + column, y + Math.min(previousTop, top),
                                x + column + 1, y + Math.max(previousTop, top) + 1, rim);
                    }
                    previousTop = top;
                }
            }

            int stepX = Math.max(3, width / 100);
            int stepY = Math.max(3, height / 70);
            for (int row = 0; row < height; row += stepY)
            {
                for (int column = 0; column < width; column += stepX)
                {
                    int random = fragmentHash(row * 977 + column * 61 + frame * 313);
                    if ((random & 255) > 92) continue;
                    if (portal && row < fallbackTop(column, columnSize, width, height, frame, activity)) continue;
                    int pieceWidth = Math.max(1, ((random >>> 8) & 3));
                    int pieceHeight = Math.max(1, ((random >>> 11) & 3));
                    int light = (int) (alpha * (0.15F + ((random >>> 15) & 31) / 55.0F));
                    int rgb = ((random >>> 21) & 3) == 0 ? 0x2674D9 : 0x153C83;
                    float red = NoiseCursorTrail.influenceAt(trail, x + column, y + row,
                            NoiseCursorTrail.RADIUS_GUI_PIXELS);
                    if (red > 0)
                    {
                        // 可调：红色过曝只增亮已有碎粒，不铺设红色背景圆。
                        int hotColor = ((random >>> 21) & 7) == 0 ? 0xFF6973 : 0xFF142B;
                        rgb = mixColor(rgb, hotColor, red);
                        light = Math.min(255, (int) (light * (1 + red * 0.7F)));
                        graphics.fill(x + Math.max(0, column - 1), y + Math.max(0, row - 1),
                                x + Math.min(width, column + pieceWidth + 1),
                                y + Math.min(height, row + pieceHeight + 1),
                                (int) (alpha * red * 0.08F) << 24 | 0xD81931);
                    }
                    graphics.fill(x + column, y + row,
                            x + Math.min(width, column + pieceWidth),
                            y + Math.min(height, row + pieceHeight), light << 24 | rgb);
                }
            }
        });
    }

    private static int mixColor(int from, int to, float amount)
    {
        int r = (int) (Mth.lerp(amount, from >> 16 & 255, to >> 16 & 255));
        int g = (int) (Mth.lerp(amount, from >> 8 & 255, to >> 8 & 255));
        int b = (int) (Mth.lerp(amount, from & 255, to & 255));
        return r << 16 | g << 8 | b;
    }

    private static int fallbackTop(int column, int columnSize, int width, int height, int frame, float activity)
    {
        int random = fragmentHash(column / columnSize * 439 + frame / 2 * 37);
        float ragged = ((random & 255) / 255.0F - 0.5F) * (0.18F + activity * 0.20F);
        int top = (int) (height * (0.54F - column / (float) width * 0.28F + ragged));
        return Mth.clamp(top / 2 * 2, 0, height - 1);
    }

    private static int fragmentHash(int value)
    {
        value = ((value >>> 16) ^ value) * 0x45D9F3B;
        value = ((value >>> 16) ^ value) * 0x45D9F3B;
        return (value >>> 16) ^ value;
    }

    private NoiseFieldRenderer()
    {
    }
}
