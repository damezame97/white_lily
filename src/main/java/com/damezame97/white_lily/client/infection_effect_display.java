package com.damezame97.white_lily.client;

import com.damezame97.white_lily.infection.infection_symptom_rules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.minecraftforge.registries.ForgeRegistries;

/** 优先显示 16×16 像素图标；资源包缺少图标时仍可回退到编号标记。 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "white_lily", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class infection_effect_display implements IClientMobEffectExtensions
{
    private static MobEffectInstance hovered;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void before(net.minecraftforge.client.event.ScreenEvent.Render.Pre event) { hovered = null; }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void after(net.minecraftforge.client.event.ScreenEvent.Render.Post event)
    {
        if (hovered == null) return;
        var client = Minecraft.getInstance();
        var lines = new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        lines.add(hovered.getEffect().getDisplayName().getVisualOrderText());
        lines.addAll(client.font.split(net.minecraft.network.chat.Component.translatable(
                hovered.getEffect().getDescriptionId() + ".description"), 240));
        event.getGuiGraphics().renderTooltip(client.font, lines, event.getMouseX(), event.getMouseY());
    }
    private void draw(MobEffectInstance instance, GuiGraphics graphics, int x, int y)
    {
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
        if (id == null) return;
        var client = Minecraft.getInstance();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
        if (client.getResourceManager().getResource(texture).isPresent())
        {
            graphics.blit(texture, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
            return;
        }
        int color = 0xFF000000 | instance.getEffect().getColor();
        graphics.fill(x, y, x + 18, y + 18, 0xFF202020);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, color);
        String number = Integer.toString(infection_symptom_rules.ALL_IDS.indexOf(id.getPath()) + 1);
        if (id.getPath().equals("bandaged")) number = "13";
        if (id.getPath().equals("wound_suppression")) number = "14";
        graphics.drawString(client.font, number, x + (18 - client.font.width(number)) / 2, y + 5, 0xFFFFFF, true);
    }
    @Override
    public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics graphics, int x, int y, int z)
    {
        draw(instance, graphics, x, y + 7);
        var client = Minecraft.getInstance();
        double mouseX = client.mouseHandler.xpos() * client.getWindow().getGuiScaledWidth() / client.getWindow().getScreenWidth();
        double mouseY = client.mouseHandler.ypos() * client.getWindow().getGuiScaledHeight() / client.getWindow().getScreenHeight();
        if (mouseX >= x && mouseX < x + 18 && mouseY >= y + 7 && mouseY < y + 25) hovered = instance;
        return true;
    }
    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z, float alpha)
    {
        draw(instance, graphics, x + 3, y + 3);
        return true;
    }
}
