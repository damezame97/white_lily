package com.damezame97.white_lily.mixin.recipebook;

import com.damezame97.white_lily.recipebook.recipe_menu_gate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractFurnaceMenu.class,ItemCombinerMenu.class,StonecutterMenu.class,GrindstoneMenu.class,LoomMenu.class,CartographyTableMenu.class})
public abstract class MixinVanillaQuickMoveGate {
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true)
    private void whiteLily$resultShift(Player player,int slot,CallbackInfoReturnable<ItemStack> callback) {
        if(!recipe_menu_gate.canTake((AbstractContainerMenu)(Object)this,slot,player)) callback.setReturnValue(ItemStack.EMPTY);
    }
}
