package com.damezame97.white_lily.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ten vanilla heart icons represent the player's current health percentage. */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT)
public final class percentage_health_overlay {
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static Player tracked;
    private static health_damage_trail trail = new health_damage_trail();

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.gui instanceof ForgeGui gui)
                || !gui.shouldDrawSurvivalElements() || !(mc.getCameraEntity() instanceof Player player)) return;
        event.setCanceled(true);
        if (tracked != player) {
            tracked = player;
            trail = new health_damage_trail();
        }
        double now = player.tickCount + event.getPartialTick();
        double max = Math.max(0.001, player.getMaxHealth());
        double ratio = Math.max(0, Math.min(1, player.getHealth() / max));
        double ghost = trail.update(player.getHealth(), max, now);
        double pending = com.damezame97.white_lily.capability.suffering_capability.get(player)
                .map(data -> (double) data.clientPending).orElse(0.0);
        double safeRatio = Math.max(0, ratio - pending / max);
        GuiGraphics graphics = event.getGuiGraphics();
        gui.setupOverlayRenderState(true, false);
        int x = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int y = event.getWindow().getGuiScaledHeight() - gui.leftHeight;
        int v = player.level().getLevelData().isHardcore() ? 45 : 0;
        int type = player.hasEffect(MobEffects.POISON) ? 4
                : player.hasEffect(MobEffects.WITHER) ? 6 : player.isFullyFrozen() ? 9 : 2;
        for (int i = 0; i < 10; i++) {
            int hx = x + i * 8;
            graphics.blit(ICONS, hx, y, 16, v, 9, 9);
            graphics.setColor(1, 0.55F, 0.55F, 0.75F);
            fill(graphics, hx, y, 70, v, ghost * 10 - i);
            graphics.setColor(1, 1, 1, 1);
            fill(graphics, hx, y, 16 + type * 18, v, ratio * 10 - i);
            // Cover only the still-owned health that is committed to delayed damage.
            int from = pixels(safeRatio * 10 - i);
            int to = pixels(ratio * 10 - i);
            if (to > from) {
                graphics.setColor(0.65F, 0.22F, 0.65F, 1);
                graphics.blit(ICONS, hx + from, y, 70 + from, v, to - from, 9);
                graphics.setColor(1, 1, 1, 1);
            }
        }
        // Absorption remains a separate vanilla-style row, outside the red percentage.
        int absorption = (int) Math.ceil(player.getAbsorptionAmount());
        int rows = (int) Math.ceil(absorption / 20.0);
        for (int i = 0; i < (absorption + 1) / 2; i++) {
            int hx = x + i % 10 * 8;
            int hy = y - (i / 10 + 1) * 10;
            graphics.blit(ICONS, hx, hy, 16, v, 9, 9);
            graphics.blit(ICONS, hx, hy, 160 + (i * 2 + 1 == absorption ? 9 : 0), v, 9, 9);
        }
        gui.leftHeight += (1 + rows) * 10;
        graphics.setColor(1, 1, 1, 1);
    }

    private static void fill(GuiGraphics graphics, int x, int y, int u, int v, double amount) {
        int width = pixels(amount);
        if (width > 0) graphics.blit(ICONS, x, y, u, v, width, 9);
    }

    private static int pixels(double amount) {
        return (int) Math.round(Math.max(0, Math.min(1, amount)) * 9);
    }
}
