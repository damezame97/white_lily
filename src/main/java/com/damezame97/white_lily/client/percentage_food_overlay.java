package com.damezame97.white_lily.client;

import java.util.UUID;
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

/** Immediate percentage food fill with a separate, pixel-edged gold saturation halo. */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT)
public final class percentage_food_overlay {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/icons.png");
    private static Player owner;
    private static int food;
    private static float saturation;
    private static float capacity = 20;
    private static int foodCapacity = 20;
    // Closed outer contour traced from the vanilla full-food sprite at icons.png (52,27).
    // Includes the diagonal bone instead of drawing a second crossed diamond below it.
    private static final String[] HALO = {
            " ####    ", "#    #   ", "#     #  ", "#     #  ", "#     #  ",
            " #     # ", "  ###   #", "     #  #", "      ###"};

    public static void accept(UUID id, int newFood, float newSaturation, float newCapacity, int newFoodCapacity) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getUUID().equals(id)) return;
        owner = player;
        food = newFood;
        foodCapacity = Math.max(1, newFoodCapacity);
        com.damezame97.white_lily.capability.food_capacity.set(player.getFoodData(), foodCapacity);
        saturation = Float.isFinite(newSaturation) ? Math.max(0, newSaturation) : 0;
        capacity = Float.isFinite(newCapacity) ? Math.max(1, newCapacity) : 20;
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
    }

    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.gui instanceof ForgeGui gui)
                || !gui.shouldDrawSurvivalElements() || !(mc.getCameraEntity() instanceof Player player)
                || player != mc.player) return;
        // Match vanilla: a living mount's health replaces food in this position.
        if (player.getVehicle() != null && player.getVehicle().showVehicleHealth()) return;
        event.setCanceled(true);
        gui.setupOverlayRenderState(true, false);
        GuiGraphics g = event.getGuiGraphics();
        double foodRatio = Math.max(0, Math.min(1, (owner == player ? food : player.getFoodData().getFoodLevel())
                / (double) (owner == player ? foodCapacity : com.damezame97.white_lily.capability.food_capacity.get(player.getFoodData()))));
        double satRatio = owner == player ? Math.max(0, Math.min(1, saturation / capacity)) : 0;
        int right = event.getWindow().getGuiScaledWidth() / 2 + 91;
        int y = event.getWindow().getGuiScaledHeight() - gui.rightHeight;
        boolean hungry = player.hasEffect(MobEffects.HUNGER);
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            g.blit(ICONS, x, y, hungry ? 133 : 16, 27, 9, 9);
            int width = pixels(foodRatio * 10 - i);
            if (width > 0) g.blit(ICONS, x + 9 - width, y,
                    (hungry ? 88 : 52) + 9 - width, 27, width, 9);
        }
        // Render the halos after all food icons, so adjacent icons cannot cover them.
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int width = pixels(satRatio * 10 - i);
            for (int row = 0; row < HALO.length; row++) {
                for (int col = 9 - width; col < 9; col++) {
                    if (HALO[row].charAt(col) == '#')
                        g.fill(x + col, y + row, x + col + 1, y + row + 1, 0xFFFFCF45);
                }
            }
        }
        gui.rightHeight += 10;
    }

    private static int pixels(double amount) {
        return (int) Math.round(Math.max(0, Math.min(1, amount)) * 9);
    }
}
