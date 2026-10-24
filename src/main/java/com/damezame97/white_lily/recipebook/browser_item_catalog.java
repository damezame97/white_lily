package com.damezame97.white_lily.recipebook;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legal item list for the administrator test browser.
 *
 * <p>It enumerates registered items plus a bounded set of real variants: potion,
 * splash/lingering potion, and enchanted book. It never invents arbitrary NBT
 * combinations. This class is common code so it can be tested on a dedicated server.</p>
 */
public final class browser_item_catalog
{
    private static volatile List<ItemStack> cache;

    public static List<ItemStack> all()
    {
        List<ItemStack> value = cache;
        if (value == null)
        {
            synchronized (browser_item_catalog.class)
            {
                value = cache;
                if (value == null)
                {
                    value = build();
                    cache = value;
                }
            }
        }
        return value;
    }

    public static void clear()
    {
        cache = null;
    }

    private static List<ItemStack> build()
    {
        Map<String, ItemStack> unique = new LinkedHashMap<>();

        for (Item item : ForgeRegistries.ITEMS.getValues())
        {
            if (item == Items.AIR) continue;
            add(unique, new ItemStack(item));
        }

        for (Potion potion : ForgeRegistries.POTIONS.getValues())
        {
            for (Item item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION))
            {
                ItemStack stack = new ItemStack(item);
                PotionUtils.setPotion(stack, potion);
                add(unique, stack);
            }
        }

        for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues())
            add(unique, EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1)));

        return List.copyOf(unique.values());
    }

    private static void add(Map<String, ItemStack> unique, ItemStack stack)
    {
        if (stack.isEmpty() || stack.getItem() == Items.AIR) return;
        ItemStack one = stack.copyWithCount(1);
        unique.putIfAbsent(key(one), one);
    }

    public static String key(ItemStack stack)
    {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + (stack.hasTag() ? stack.getTag().toString() : "");
    }

    private browser_item_catalog() { }
}
