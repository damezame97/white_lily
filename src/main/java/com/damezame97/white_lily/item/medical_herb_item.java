package com.damezame97.white_lily.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.List;
import javax.annotation.Nullable;

/** 对合适土壤点按放置；对空气按住右键完成后使用药草。 */
public final class medical_herb_item extends BlockItem
{
    private final medical_actions.Kind kind;
    public medical_herb_item(Block block, medical_actions.Kind kind)
    { super(block, new Properties().stacksTo(64)); this.kind = kind; }
    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.EAT; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    { return ItemUtils.startUsingInstantly(level, player, hand); }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    { return medical_actions.finish(stack, level, entity, kind); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    { medical_item.appendDescription(getDescriptionId(), level, lines); }
    @Override public String getDescriptionId() { return "item.white_lily." + kindId(); }
    private String kindId()
    { return switch (kind) { case BITTERLEAF -> "bitterleaf"; case YARROW -> "yarrow"; default -> "calming_flower"; }; }
}
