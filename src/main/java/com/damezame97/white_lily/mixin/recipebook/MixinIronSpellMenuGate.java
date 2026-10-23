package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.recipe_menu_gate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** ISS uses its own shift methods; check the same identified output slots before moving. */
@Pseudo
@Mixin(targets={"io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu","io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu"},remap=false)
public abstract class MixinIronSpellMenuGate {
    @Inject(method="m_7648_",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void whiteLily$spellResultShiftProduction(Player player,int slot,CallbackInfoReturnable<ItemStack> callback) {
        if(!recipe_menu_gate.canTake((AbstractContainerMenu)(Object)this,slot,player)) callback.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private void whiteLily$spellResultShiftDevelopment(Player player,int slot,CallbackInfoReturnable<ItemStack> callback) {
        if(!recipe_menu_gate.canTake((AbstractContainerMenu)(Object)this,slot,player)) callback.setReturnValue(ItemStack.EMPTY);
    }
}
