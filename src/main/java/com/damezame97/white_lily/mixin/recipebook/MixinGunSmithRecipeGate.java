package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.recipe_menu_gate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** TaCZ's recipe-ID server entry point, verified against the bundled 1.1.8 hotfix. */
@Pseudo
@Mixin(targets="com.tacz.guns.inventory.GunSmithTableMenu",remap=false)
public abstract class MixinGunSmithRecipeGate {
    @Inject(method="doCraft",at=@At("HEAD"),cancellable=true,remap=false)
    private void whiteLily$lockedGunRecipe(ResourceLocation recipe,Player player,CallbackInfo callback) {
        if(!recipe_menu_gate.permitted(player,recipe)) callback.cancel();
    }
}
