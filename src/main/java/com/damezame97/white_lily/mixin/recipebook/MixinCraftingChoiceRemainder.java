package com.damezame97.white_lily.mixin.recipebook;

import com.damezame97.white_lily.recipebook.crafting_choice;
import com.damezame97.white_lily.mixin.research.ResearchCraftingContainerAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class MixinCraftingChoiceRemainder {
    @Shadow @Final private CraftingContainer craftSlots;
    @Unique private CraftingRecipe whiteLily$consumed;
    @Unique private AbstractContainerMenu whiteLily$menu;
    @Inject(method="onTake",at=@At("HEAD")) private void whiteLily$capture(Player player,ItemStack output,CallbackInfo ci){
        whiteLily$menu=craftSlots instanceof ResearchCraftingContainerAccess access?access.whiteLily$researchMenu():null;
        whiteLily$consumed=whiteLily$menu==null?null:crafting_choice.beginConsume(whiteLily$menu,player);
    }
    @Redirect(method="onTake",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/crafting/RecipeManager;getRemainingItemsFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
    private NonNullList<ItemStack> whiteLily$selectedRemainder(RecipeManager manager,RecipeType<?> type,Container grid,Level level){
        if(whiteLily$menu!=null&&crafting_choice.supports(whiteLily$menu)&&!level.isClientSide)
            return whiteLily$consumed==null?NonNullList.withSize(grid.getContainerSize(),ItemStack.EMPTY):whiteLily$consumed.getRemainingItems(craftSlots);
        if(whiteLily$menu!=null&&crafting_choice.supports(whiteLily$menu)&&level.isClientSide){
            net.minecraft.resources.ResourceLocation id=net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.damezame97.white_lily.client.crafting_choice_client.selected(whiteLily$menu));
            var recipe=id==null?null:manager.byKey(id).orElse(null);
            return recipe instanceof CraftingRecipe craft?craft.getRemainingItems(craftSlots):NonNullList.withSize(grid.getContainerSize(),ItemStack.EMPTY);
        }
        return manager.getRemainingItemsFor(RecipeType.CRAFTING,craftSlots,level);
    }
    @Inject(method="onTake",at=@At("RETURN")) private void whiteLily$finish(Player player,ItemStack output,CallbackInfo ci){
        if(whiteLily$menu!=null)crafting_choice.endConsume(whiteLily$menu,player);whiteLily$consumed=null;whiteLily$menu=null;
    }
}
