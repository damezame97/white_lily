package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.effect_bar_layout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-only effect rail between the inventory and the recipe browser.
 *
 * <p>Geometry lives in {@link effect_bar_layout}; this class only maps live effect data to
 * that geometry. Ordering is by registry id, so remaining time never changes the order.</p>
 */
public final class effect_sidebar
{
    private static final String KEY = "screen.white_lily.";
    private static final int GAP = 2;
    @Nullable private static MobEffectInstance hovered;
    @Nullable private static Screen hoveredScreen;

    public static boolean shouldReplaceEffects(EffectRenderingInventoryScreen<?> screen)
    {
        return !effects().isEmpty();
    }

    public static List<MobEffectInstance> effects()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();
        List<MobEffectInstance> result = new ArrayList<>();
        for (MobEffectInstance instance : mc.player.getActiveEffects())
            if ((instance.getDuration() > 0 || instance.isInfiniteDuration())
                    && ForgeHooksClient.shouldRenderEffect(instance)) result.add(instance);
        result.sort(Comparator.comparing(instance -> String.valueOf(id(instance))));
        return result;
    }

    @Nullable public static ResourceLocation id(MobEffectInstance instance)
    {
        return ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
    }

    public static int barX(EffectRenderingInventoryScreen<?> screen)
    {
        return screen.getGuiLeft() + screen.getXSize() + GAP;
    }

    public static int width(EffectRenderingInventoryScreen<?> screen)
    {
        List<MobEffectInstance> effects = effects();
        if (effects.isEmpty()) return 0;
        int top = screen.getGuiTop() + 1;
        int height = Math.max(0, screen.getYSize() - 2);
        effect_bar_layout.Layout layout = effect_bar_layout.layout(effects.size(), top, height);
        if (!layout.footer()) return effect_bar_layout.SLOT;
        int text = Minecraft.getInstance().font.width(countText(effects.size())) + 4;
        return Math.max(effect_bar_layout.SLOT, text);
    }

    /** Pixels the recipe browser must leave free immediately to the right of the inventory. */
    public static int reservedWidth(Screen screen)
    {
        if (!(screen instanceof EffectRenderingInventoryScreen<?> effectScreen)) return 0;
        int width = width(effectScreen);
        if (width <= 0) return 0;
        int available = Math.max(effect_bar_layout.SLOT, screen.width - barX(effectScreen) - 2);
        return Math.min(width, available) + GAP;
    }

    public static boolean overlaps(Screen screen, int x, int y, int w, int h)
    {
        if (!(screen instanceof EffectRenderingInventoryScreen<?> effectScreen)) return false;
        int width = width(effectScreen);
        if (width <= 0) return false;
        int bx = barX(effectScreen), by = effectScreen.getGuiTop();
        return x < bx + width && x + w > bx && y < by + effectScreen.getYSize() && y + h > by;
    }

    public static void renderBase(Screen screen, GuiGraphics graphics, int mouseX, int mouseY)
    {
        hovered = null;
        hoveredScreen = null;
        if (!(screen instanceof EffectRenderingInventoryScreen<?> effectScreen)) return;
        List<MobEffectInstance> effects = effects();
        if (effects.isEmpty()) return;

        int width = width(effectScreen);
        if (width <= 0) return;
        int x = barX(effectScreen);
        int top = effectScreen.getGuiTop() + 1;
        int height = Math.max(0, effectScreen.getYSize() - 2);
        effect_bar_layout.Layout layout = effect_bar_layout.layout(effects.size(), top, height);

        int hit = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + height
                ? effect_bar_layout.topmost(mouseY, top, layout.step(), effects.size()) : -1;
        for (int i = 0; i < effects.size(); i++)
        {
            int y = effect_bar_layout.y(top, layout.step(), i);
            boolean selected = i == hit;
            graphics.fill(x, y, x + effect_bar_layout.SLOT, y + effect_bar_layout.SLOT,
                    selected ? 0xE02A3540 : 0xE0141B23);
            graphics.renderOutline(x, y, effect_bar_layout.SLOT, effect_bar_layout.SLOT,
                    selected ? 0xFFFFD578 : 0xFF526070);
            drawIcon(graphics, effects.get(i), x + 1, y + 1);
        }
        if (layout.footer())
        {
            Component count = countText(effects.size());
            graphics.drawString(Minecraft.getInstance().font, count,
                    x + Math.max(0, (width - Minecraft.getInstance().font.width(count)) / 2),
                    layout.footerY(), 0xFFE7E2D6, false);
        }
        if (hit >= 0)
        {
            hovered = effects.get(hit);
            hoveredScreen = screen;
        }
    }

    public static void renderForeground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY)
    {
        if (hovered == null || hoveredScreen != screen
                || effects().stream().noneMatch(instance -> java.util.Objects.equals(id(instance), id(hovered))))
            return;
        List<FormattedCharSequence> lines = tooltip(hovered);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.renderTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
        graphics.pose().popPose();
    }

    public static boolean click(Screen screen, double mouseX, double mouseY, int button)
    {
        if (button != 0 || !(screen instanceof EffectRenderingInventoryScreen<?> effectScreen)) return false;
        List<MobEffectInstance> effects = effects();
        if (effects.isEmpty()) return false;
        int width = width(effectScreen);
        int x = barX(effectScreen);
        int top = effectScreen.getGuiTop() + 1;
        int height = Math.max(0, effectScreen.getYSize() - 2);
        if (mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= top + height) return false;
        effect_bar_layout.Layout layout = effect_bar_layout.layout(effects.size(), top, height);
        int hit = effect_bar_layout.topmost(mouseY, top, layout.step(), effects.size());
        if (hit < 0) return false;
        recipe_browser.openModal(new effect_list_screen(effectScreen, id(effects.get(hit))));
        return true;
    }

    public static Component name(MobEffectInstance instance)
    {
        MutableComponent name = instance.getEffect().getDisplayName().copy();
        if (instance.getAmplifier() > 0)
        {
            int level = instance.getAmplifier() + 1;
            name.append(" ").append(level <= 10
                    ? Component.translatable("enchantment.level." + level)
                    : Component.literal(Integer.toString(level)));
        }
        return name;
    }

    public static Component duration(MobEffectInstance instance)
    {
        return MobEffectUtil.formatDuration(instance, 1.0F);
    }

    public static List<Component> description(MobEffectInstance instance)
    {
        String key = instance.getEffect().getDescriptionId() + ".description";
        if (!I18n.exists(key)) return List.of();
        List<Component> result = new ArrayList<>();
        result.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        return result;
    }

    public static List<FormattedCharSequence> tooltip(MobEffectInstance instance)
    {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(name(instance).getVisualOrderText());
        lines.add(Component.translatable(KEY + "effect.duration", duration(instance))
                .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        for (Component detail : description(instance))
            lines.addAll(Minecraft.getInstance().font.split(detail, 220));
        return lines;
    }

    public static void drawIcon(GuiGraphics graphics, MobEffectInstance instance, int x, int y)
    {
        graphics.blit(x, y, 0, 18, 18, Minecraft.getInstance().getMobEffectTextures().get(instance.getEffect()));
    }

    public static Component countText(int count)
    {
        return Component.translatable(KEY + "effect_bar.count", count);
    }

    private effect_sidebar() { }
}
