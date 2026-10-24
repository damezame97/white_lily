package com.damezame97.white_lily.crafting;

import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Objects;

/**
 * Knowledge-book transcription: exactly one held template is returned unchanged (including NBT),
 * while the ordinary book, ink sac and feather are consumed by the native result-slot path.
 *
 * <p>The template return belongs to this recipe only; book items themselves do not declare a
 * global crafting remainder and therefore cannot affect reading or unrelated recipes.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class template_copy_recipe extends ShapelessRecipe
{
    private final Ingredient template;

    private template_copy_recipe(ShapelessRecipe recipe, Ingredient template)
    {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
        this.template = template;
    }

    public Ingredient template() { return template; }

    @Override public boolean matches(CraftingContainer container, Level level)
    {
        int templates = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            if (template.test(container.getItem(slot))) templates++;
        return templates == 1 && super.matches(container, level);
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer container)
    {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < container.getContainerSize(); slot++)
        {
            ItemStack stack = container.getItem(slot);
            if (!template.test(stack)) continue;
            // Exactly one template returns; the native ResultSlot merges it back into the emptied slot.
            remaining.set(slot, stack.copyWithCount(1));
            break;
        }
        return remaining;
    }

    @Override public RecipeSerializer<?> getSerializer() { return prescription_recipes.TEMPLATE_COPY.get(); }

    public static final class Serializer implements RecipeSerializer<template_copy_recipe>
    {
        @Override public template_copy_recipe fromJson(ResourceLocation id, JsonObject json)
        {
            ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json);
            if (!json.has("template")) throw new IllegalArgumentException("Missing template item for " + id);
            ResourceLocation templateId = ResourceLocation.tryParse(json.get("template").getAsString());
            Item item = templateId == null ? null : ForgeRegistries.ITEMS.getValue(templateId);
            if (item == null) throw new IllegalArgumentException("Unknown template item for " + id);
            Ingredient template = Ingredient.of(item);
            boolean present = base.getIngredients().stream()
                    .anyMatch(ingredient -> Arrays.stream(ingredient.getItems()).anyMatch(stack -> stack.is(item)));
            if (!present) throw new IllegalArgumentException("Template is not an ingredient of " + id);
            return new template_copy_recipe(base, template);
        }

        @Override public template_copy_recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            ShapelessRecipe base = Objects.requireNonNull(RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer),
                    "Missing template copy recipe");
            return new template_copy_recipe(base, Ingredient.fromNetwork(buffer));
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, template_copy_recipe recipe)
        {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
            recipe.template.toNetwork(buffer);
        }
    }
}
