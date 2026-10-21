package com.damezame97.white_lily.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

public final class medical_item extends Item
{
    private final medical_actions.Kind kind;
    public medical_item(medical_actions.Kind kind)
    {
        super(new Properties().stacksTo(kind == medical_actions.Kind.TEA ? 1 : 16));
        this.kind = kind;
    }
    @Override public int getUseDuration(ItemStack stack)
    { return kind == medical_actions.Kind.SIMPLE || kind == medical_actions.Kind.FINE ? 60 : 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack)
    { return kind == medical_actions.Kind.TEA ? UseAnim.DRINK : UseAnim.EAT; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return medical_actions.canUse(player, kind) ? ItemUtils.startUsingInstantly(level, player, hand)
                : InteractionResultHolder.fail(player.getItemInHand(hand));
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    { return medical_actions.finish(stack, level, entity, kind); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    { appendDescription(getDescriptionId(), level, lines); }
    public static void appendDescription(String key, @Nullable Level level, List<Component> lines)
    {
        if (!detail_tooltip.isExpanded(level)) { lines.add(detail_tooltip.hint()); return; }
        for (int i = 1; i <= 3; i++) lines.add(Component.translatable(key + ".description." + i).withStyle(ChatFormatting.GRAY));
    }
}
