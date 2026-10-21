package com.damezame97.white_lily.crafting;

import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import javax.annotation.ParametersAreNonnullByDefault;

/** 原版无序配方，只在 ResultSlot 处理余料时返还固定损伤一次的剪刀副本。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class durable_tool_recipe extends ShapelessRecipe
{
    private durable_tool_recipe(ShapelessRecipe recipe)
    {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
    }

    @Override public boolean matches(CraftingContainer container, Level level)
    {
        int tools = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (stack.is(Items.SHEARS))
            {
                tools++;
                if (stack.getMaxDamage() <= 0 || stack.getDamageValue() >= stack.getMaxDamage()) return false;
            }
        }
        return tools == 1 && super.matches(container, level);
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer container)
    {
        NonNullList<ItemStack> remaining = super.getRemainingItems(container);
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!stack.is(Items.SHEARS)) continue;
            // 不调用 hurt/hurtAndBreak，耐久附魔不能概率免除本次固定损耗。
            ItemStack tool = stack.copyWithCount(1);
            int damage = tool.getDamageValue() + 1;
            if (damage >= tool.getMaxDamage()) remaining.set(slot, ItemStack.EMPTY);
            else
            {
                tool.setDamageValue(damage);
                remaining.set(slot, tool);
            }
        }
        return remaining;
    }

    @Override public RecipeSerializer<?> getSerializer() { return prescription_recipes.DURABLE_TOOL.get(); }

    public static final class Serializer implements RecipeSerializer<durable_tool_recipe>
    {
        @Override public durable_tool_recipe fromJson(ResourceLocation id, JsonObject json)
        { return new durable_tool_recipe(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json)); }

        @Override public durable_tool_recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            return new durable_tool_recipe(java.util.Objects.requireNonNull(
                    RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer), "Missing durable tool recipe"));
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, durable_tool_recipe recipe)
        { RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe); }
    }
}
