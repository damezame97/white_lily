package com.damezame97.white_lily.item;

import com.damezame97.white_lily.attribute.attributes;
import com.damezame97.white_lily.capability.food_capacity;
import com.damezame97.white_lily.gamerule.happy_mode;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Reusable, self-targeted test tools. Possession is sufficient in every game mode. */
public final class state_test_item extends Item {
    public enum Action { HEALTH, FOOD, DAMAGE }
    private final Action action;

    public state_test_item(Action action) {
        super(new Properties().stacksTo(1));
        this.action = action;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.getCooldowns().isOnCooldown(this))
            return InteractionResultHolder.fail(stack);
        if (!level.isClientSide) {
            switch (action) {
                case HEALTH -> player.setHealth(player.getMaxHealth());
                case FOOD -> {
                    var food = player.getFoodData();
                    int cap = food_capacity.get(food);
                    double extra = level.getGameRules().getBoolean(happy_mode.RULE)
                            ? Math.max(0, player.getAttributeValue(attributes.BUILDING_ENDURANCE.get())) : 0;
                    food.setFoodLevel(cap);
                    food.setSaturation((float) (cap + extra));
                    food.setExhaustion(0.0F);
                }
                case DAMAGE -> {
                    // Deliberately bypass combat damage modifiers, absorption and invulnerability.
                    player.setHealth(Math.max(0.0F, player.getHealth() - 10.0F));
                    if (player.getHealth() <= 0.0F) player.die(player.damageSources().genericKill());
                }
            }
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("tooltip.white_lily.state_test.reusable").withStyle(ChatFormatting.GRAY));
    }
}
