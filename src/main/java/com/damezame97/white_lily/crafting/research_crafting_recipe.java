package com.damezame97.white_lily.crafting;

import com.damezame97.white_lily.research.research_crafting_gate;
import com.google.gson.JsonObject;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import javax.annotation.ParametersAreNonnullByDefault;

/** 无序配方的材料/NBT规则仍由原版与Forge处理；研究权限属于当前网格的玩家。 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class research_crafting_recipe extends ShapelessRecipe
{
    private research_crafting_recipe(ShapelessRecipe recipe)
    {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getResultItem(RegistryAccess.EMPTY), recipe.getIngredients());
    }

    @Override public boolean matches(CraftingContainer container, Level level)
    {
        return research_crafting_gate.allowed(container, getId()) && super.matches(container, level);
    }

    @Override public RecipeSerializer<?> getSerializer() { return prescription_recipes.RESEARCH_CRAFTING.get(); }

    public static final class Serializer implements RecipeSerializer<research_crafting_recipe>
    {
        @Override public research_crafting_recipe fromJson(ResourceLocation id, JsonObject json)
        { return new research_crafting_recipe(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json)); }

        @Override public research_crafting_recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer)
        {
            return new research_crafting_recipe(java.util.Objects.requireNonNull(
                    RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer), "Missing research crafting recipe"));
        }

        @Override public void toNetwork(FriendlyByteBuf buffer, research_crafting_recipe recipe)
        { RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe); }
    }
}
