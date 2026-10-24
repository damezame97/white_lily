package com.damezame97.white_lily.mixin.recipebook;

import com.damezame97.white_lily.recipebook.crafting_choice;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The whole vanilla Shift loop uses one recipe ID, including identical-output recipes. */
@Mixin(AbstractContainerMenu.class)
public abstract class MixinCraftingChoiceAction {
    @Invoker("doClick") protected abstract void whiteLily$doClick(int slot,int button,ClickType action,Player player);
    @Redirect(method="clicked",at=@At(value="INVOKE",target="Lnet/minecraft/world/inventory/AbstractContainerMenu;doClick(IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"))
    private void whiteLily$oneChoice(AbstractContainerMenu menu,int slot,int button,ClickType action,Player player){
        crafting_choice.beginAction(menu,player,slot,action);
        try{whiteLily$doClick(slot,button,action,player);}finally{crafting_choice.endAction(menu,player);}
    }
}
