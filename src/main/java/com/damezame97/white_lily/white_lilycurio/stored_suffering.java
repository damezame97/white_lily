package com.damezame97.white_lily.white_lilycurio;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.damezame97.white_lily.procedures.stored_suffering_events;
import com.damezame97.white_lily.item.detail_tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 普通 ring 栏戒指，不继承改良饰品的绑定与背包清理规则。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
// Level 由游戏引擎持有；这里只借用，不得关闭世界。
@SuppressWarnings("resource")
public class stored_suffering extends Item implements ICurioItem
{
    public static final String NAME = "stored_suffering";
    public static final String INSTANCE_ID = "white_lily:ring_instance";

    public stored_suffering()
    {
        super(new Item.Properties().durability(512));
    }

    public static UUID identity(ItemStack stack)
    {
        if (!stack.getOrCreateTag().hasUUID(INSTANCE_ID))
        {
            stack.getOrCreateTag().putUUID(INSTANCE_ID, UUID.randomUUID());
        }
        return stack.getOrCreateTag().getUUID(INSTANCE_ID);
    }

    @Override
    public Component getName(ItemStack stack)
    {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.DARK_RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        super.appendHoverText(stack, level, tooltip, flag);
        appendDescription(tooltip, detail_tooltip.isExpanded(level));
    }

    public void appendDescription(List<Component> tooltip, boolean expanded)
    {
        tooltip.add(Component.translatable(getDescriptionId() + ".flavor").withStyle(ChatFormatting.DARK_PURPLE));
        if (!expanded)
        {
            tooltip.add(detail_tooltip.hint());
            return;
        }
        // 每行单独构造组件，普通说明采用与项目其他材料相同的灰色，不继承风味行颜色。
        for (int line = 1; line <= 4; line++)
        {
            tooltip.add(Component.translatable(getDescriptionId() + ".description." + line).withStyle(ChatFormatting.GRAY));
        }
        for (int line = 1; line <= 4; line++)
        {
            tooltip.add(Component.translatable(getDescriptionId() + ".effect." + line).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack)
    {
        return "ring".equals(context.identifier()) && !context.cosmetic();
    }

    @Override
    public void onEquip(SlotContext context, ItemStack previous, ItemStack stack)
    {
        if (!context.entity().level().isClientSide && !context.cosmetic())
        {
            identity(stack);
        }
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack next, ItemStack stack)
    {
        if (!context.cosmetic() && context.entity() instanceof Player player && !player.level().isClientSide)
        {
            // Curios 在耐久/NBT 改变时也会调用 onUnequip；同一戒指的这种更新不是摘下。
            CompoundTag previousTag = stack.getTag();
            CompoundTag nextTag = next.getTag();
            if (next.getItem() == this && previousTag != null && previousTag.hasUUID(INSTANCE_ID)
                    && nextTag != null && nextTag.hasUUID(INSTANCE_ID)
                    && previousTag.getUUID(INSTANCE_ID).equals(nextTag.getUUID(INSTANCE_ID)))
            {
                return;
            }
            stored_suffering_events.onRingRemoved(player);
        }
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book)
    {
        return false;
    }

    @Override
    public boolean isRepairable(ItemStack stack)
    {
        return false;
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment)
    {
        return 0;
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack)
    {
        return Map.of();
    }
}
