package com.damezame97.white_lily.mixin.recipebook;

import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the crafting grid preview empty until the player has personally unlocked the recipe. */
@Mixin(CraftingMenu.class)
public abstract class MixinCraftingPreviewGate
{
    @Inject(method="slotChangedCraftingGrid",at=@At("HEAD"),cancellable=true)
    private static void whiteLily$lockedPreview(AbstractContainerMenu menu,Level level,Player player,
                                                CraftingContainer grid,ResultContainer result,CallbackInfo callback)
    {
        if(player!=null&&!level.isClientSide&&com.damezame97.white_lily.recipebook.crafting_choice.supports(menu)){
            com.damezame97.white_lily.recipebook.crafting_choice.update(menu,player);callback.cancel();
        }
    }
}
