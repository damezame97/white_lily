package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu",remap=false)
public abstract class MixinInscriptionCommitGate {
    @Inject(method="doInscription",at=@At("HEAD"),cancellable=true,remap=false)
    private void whiteLily$inscriptionCommit(int index,CallbackInfo callback) {
        Player player=recipe_menu_gate.owner((AbstractContainerMenu)(Object)this);
        if(player==null || !recipe_menu_gate.permitted(player,recipe_menu_gate.operation("inscription"))) callback.cancel();
    }
}
