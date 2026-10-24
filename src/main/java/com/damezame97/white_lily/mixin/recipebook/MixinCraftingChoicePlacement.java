package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.crafting_choice;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla/recipe-book compatible transfer routes retain the recipe they explicitly requested. */
@Mixin(ServerPlaceRecipe.class)
public abstract class MixinCraftingChoicePlacement {
    @Inject(method="recipeClicked",at=@At("RETURN"))
    private void whiteLily$filledChoice(ServerPlayer player,Recipe<?> recipe,boolean maximum,CallbackInfo ci){if(recipe!=null&&crafting_choice.supports(player.containerMenu))crafting_choice.selectFilled(player,player.containerMenu,recipe.getId());}
}
