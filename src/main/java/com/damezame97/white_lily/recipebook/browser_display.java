package com.damezame97.white_lily.recipebook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import java.util.List;
/** Immutable device display data, safe to verify on a dedicated server. */
public final class browser_display {
    public record DisplaySlot(Ingredient ingredient, int count, int x, int y, Component label, boolean output)
    {
        public ItemStack stack(long cycle)
        {
            ItemStack[] choices = ingredient.getItems();
            if (choices.length == 0) return ItemStack.EMPTY;
            ItemStack result = choices[Math.floorMod(cycle, choices.length)].copy();
            result.setCount(Math.max(1, count));
            return result;
        }
    }
    public record Display(Recipe<?> recipe, ResourceLocation category, Component title, ItemStack workstation,
                          List<DisplaySlot> slots, List<ItemStack> outputs, List<Component> notes, int contentHeight)
    { }

    private browser_display(){}
}
