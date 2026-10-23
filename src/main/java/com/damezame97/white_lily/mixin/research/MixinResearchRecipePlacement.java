package com.damezame97.white_lily.mixin.research;

import com.damezame97.white_lily.crafting.research_crafting_recipe;
import com.damezame97.white_lily.research.research_crafting_gate;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原版按物品ID还原配料会丢失Potion标签；仅为研究型无序配方恢复其真正配料示例。 */
@Mixin(ServerPlaceRecipe.class)
public abstract class MixinResearchRecipePlacement
{
    @Unique private research_crafting_recipe whiteLily$placingResearch;
    @Unique private int whiteLily$ingredientIndex;

    @Inject(method = "recipeClicked", at = @At("HEAD"), cancellable = true)
    private void whiteLily$researchPlace(ServerPlayer player, Recipe<?> recipe, boolean placeAll, CallbackInfo callback)
    {
        whiteLily$placingResearch = recipe instanceof research_crafting_recipe restricted ? restricted : null;
        whiteLily$ingredientIndex = 0;
        if (recipe!=null && !com.damezame97.white_lily.recipebook.recipe_unlocks.isUnlocked(player,recipe.getId()))
        {
            whiteLily$placingResearch = null;
            callback.cancel();
        }
    }

    @Inject(method = "recipeClicked", at = @At("RETURN"))
    private void whiteLily$researchPlaceFinished(ServerPlayer player, Recipe<?> recipe, boolean placeAll, CallbackInfo callback)
    { whiteLily$placingResearch = null; }

    @Redirect(method = "addItemToSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/StackedContents;fromStackingIndex(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack whiteLily$researchIngredientExample(int itemIndex)
    {
        research_crafting_recipe recipe = whiteLily$placingResearch;
        int ingredient = whiteLily$ingredientIndex++;
        if (recipe != null && ingredient < recipe.getIngredients().size())
            for (ItemStack example : recipe.getIngredients().get(ingredient).getItems())
                if (!example.isEmpty() && StackedContents.getStackingIndex(example) == itemIndex) return example.copy();
        return StackedContents.fromStackingIndex(itemIndex);
    }
}
