package com.damezame97.white_lily.mixin.recipebook;

import com.damezame97.white_lily.recipebook.recipe_menu_gate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local source change implementing the requested recipe locking; only known result slots are checked. */
@Mixin(AbstractContainerMenu.class)
public abstract class MixinMenuRecipeGate {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void whiteLily$resultClick(int slot,int button,ClickType action,Player player,CallbackInfo callback) {
        if(!recipe_menu_gate.canTake((AbstractContainerMenu)(Object)this,slot,player)) callback.cancel();
    }
}
