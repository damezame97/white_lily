package com.damezame97.white_lily.crafting;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** 保留无序配方和配方书行为，仅为水瓶补充一次合成对应的空瓶余料。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class prescription_revealing_recipe extends ShapelessRecipe
{
    public prescription_revealing_recipe(ResourceLocation id, String group, CraftingBookCategory category,
                                         ItemStack result, NonNullList<Ingredient> ingredients)
    {
        super(id, group, category, result, ingredients);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            if (container.getItem(slot).is(Items.POTION))
            {
                remaining.set(slot, new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer()
    {
        return prescription_recipes.REVEALING.get();
    }

    public static class Serializer implements RecipeSerializer<prescription_revealing_recipe>
    {
        private prescription_revealing_recipe wrap(ShapelessRecipe recipe)
        {
            return new prescription_revealing_recipe(recipe.getId(), recipe.getGroup(), recipe.category(),
                    recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
        }

        @Override
        public prescription_revealing_recipe fromJson(ResourceLocation id, JsonObject json)
        {
            return wrap(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json));
        }

        @Override
        public prescription_revealing_recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            return wrap(java.util.Objects.requireNonNull(RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer),
                    "Missing shapeless recipe in network packet"));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, prescription_revealing_recipe recipe)
        {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
