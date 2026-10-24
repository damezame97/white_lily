package com.damezame97.white_lily.item;

import com.damezame97.white_lily.capability.food_capacity;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class capacity_test_item extends Item {
    public static final UUID HEALTH_MODIFIER = UUID.fromString("ed8bf858-9de7-4b71-9fd9-c6d4dd2fc978");
    private final boolean health;
    private final int delta;
    public capacity_test_item(boolean health, int delta) {
        super(new Properties().stacksTo(1));
        this.health = health;
        this.delta = delta;
    }

    public static void changeHealth(Player player, int delta) {
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        var previous = attr.getModifier(HEALTH_MODIFIER);
        double amount = previous == null ? 0 : previous.getAmount();
        // Evaluate the attribute's real effective value, including other modifiers.
        double before = attr.getValue();
        double target = Math.max(1, before + delta);
        double scale = 1;
        double baseMultiplier = 1;
        for (var modifier : attr.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))
            baseMultiplier += modifier.getAmount();
        scale *= baseMultiplier;
        for (var modifier : attr.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))
            scale *= 1 + modifier.getAmount();
        if (!(scale > 0) || !Double.isFinite(scale)) return;
        attr.removeModifier(HEALTH_MODIFIER);
        attr.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, "Admin capacity test",
                amount + (target - before) / scale, AttributeModifier.Operation.ADDITION));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            double before = health ? player.getMaxHealth() : food_capacity.get(player.getFoodData());
            if (delta != 0) {
                if (health) changeHealth(player, delta);
                else {
                    food_capacity.set(player.getFoodData(), food_capacity.get(player.getFoodData()) + delta);
                    double extra = player.level().getGameRules().getBoolean(com.damezame97.white_lily.gamerule.happy_mode.RULE)
                            ? Math.max(0, player.getAttributeValue(com.damezame97.white_lily.attribute.attributes.BUILDING_ENDURANCE.get())) : 0;
                    player.getFoodData().setSaturation((float) Math.min(player.getFoodData().getSaturationLevel(),
                            food_capacity.get(player.getFoodData()) + extra));
                }
                double after = health ? player.getMaxHealth() : food_capacity.get(player.getFoodData());
                player.displayClientMessage(Component.translatable(health ? "message.white_lily.capacity.health"
                        : "message.white_lily.capacity.food", number(before), number(after)), false);
            } else {
                player.displayClientMessage(Component.translatable("message.white_lily.capacity.reading",
                        number(player.getHealth()), number(player.getMaxHealth()), player.getFoodData().getFoodLevel(),
                        food_capacity.get(player.getFoodData())), false);
            }
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    @Override public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
            java.util.List<Component> lines, TooltipFlag flag) {
        if (delta != 0) lines.add(Component.translatable(health ? "tooltip.white_lily.capacity.health_delta"
                : "tooltip.white_lily.capacity.food_delta", (delta > 0 ? "+" : "") + delta)
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
        lines.add(Component.translatable(delta == 0 ? "tooltip.white_lily.capacity.reader" : "tooltip.white_lily.capacity.change")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
