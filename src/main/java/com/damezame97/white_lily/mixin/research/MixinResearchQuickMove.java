package com.damezame97.white_lily.mixin.research;

import com.damezame97.white_lily.research.research_crafting_gate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CraftingMenu.class, InventoryMenu.class})
public abstract class MixinResearchQuickMove
{
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void whiteLily$researchShift(Player player, int index, CallbackInfoReturnable<ItemStack> callback)
    {
        if (index == 0 && !research_crafting_gate.canTake(((AbstractContainerMenu)(Object)this).getSlot(0), player))
            callback.setReturnValue(ItemStack.EMPTY);
    }
}
