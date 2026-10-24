package com.damezame97.white_lily.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** Shared tier and tooltip rules for the five independent makeshift iron tools. */
public final class makeshift_iron_tool
{
    /**
     * Same mining level, speed, attack bonus, enchantability and repair material as real iron,
     * but the durability is read from the corresponding registered iron tool and divided by 3.
     */
    public static Tier tier(Item correspondingIronTool)
    {
        int uses = Math.max(1, correspondingIronTool.getMaxDamage() / 3);
        return new Tier()
        {
            @Override public int getUses() { return uses; }
            @Override public float getSpeed() { return Tiers.IRON.getSpeed(); }
            @Override public float getAttackDamageBonus() { return Tiers.IRON.getAttackDamageBonus(); }
            @Override public int getLevel() { return Tiers.IRON.getLevel(); }
            @Override public int getEnchantmentValue() { return Tiers.IRON.getEnchantmentValue(); }
            @Override public Ingredient getRepairIngredient() { return Tiers.IRON.getRepairIngredient(); }
        };
    }

    public static void appendHoverText(Item correspondingIronTool, ItemStack stack, List<Component> tooltip,
                                       boolean expanded)
    {
        detail_tooltip.appendDetails(tooltip, expanded, lines ->
        {
            lines.add(Component.translatable("tooltip.white_lily.makeshift_iron.lore")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            lines.add(Component.translatable("tooltip.white_lily.makeshift_iron.durability",
                    stack.getMaxDamage(), correspondingIronTool.getMaxDamage()));
        });
    }

    private makeshift_iron_tool() { }
}
