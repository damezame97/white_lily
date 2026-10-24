package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EnchantmentMenu.class)
public abstract class MixinEnchantmentGate {
    @Inject(method="clickMenuButton",at=@At("HEAD"),cancellable=true)
    private void whiteLily$enchant(Player player,int button,CallbackInfoReturnable<Boolean> callback) {
        if(!recipe_menu_gate.permitted(player,recipe_menu_gate.operation("enchanting"))) callback.setReturnValue(false);
    }
}
