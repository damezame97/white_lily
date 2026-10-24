package com.damezame97.white_lily.item;

import com.damezame97.white_lily.infection.infection_service;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.Locale;

public final class pollution_test_item extends Item
{
    private final int delta;
    private final ChatFormatting color;
    public pollution_test_item(int delta, ChatFormatting color) { super(new Properties().stacksTo(64)); this.delta = delta; this.color = color; }
    @Override public Component getName(ItemStack stack) { return super.getName(stack).copy().withStyle(color); }
    @Override public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                                          java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag)
    { medical_item.appendDescription(getDescriptionId(), level, lines); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide)
        {
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (delta > 0) infection_service.addDirect(player, delta);
            else if (delta < 0) infection_service.reduce(player, -delta);
            player.displayClientMessage(Component.translatable("message.white_lily.pollution.reading",
                    String.format(Locale.ROOT, "%.1f", infection_service.query(player))), false);
            player.getCooldowns().addCooldown(this, 10);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
