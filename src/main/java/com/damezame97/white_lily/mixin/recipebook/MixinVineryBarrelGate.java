package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.recipe_machine_owner;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity",remap=false)
public abstract class MixinVineryBarrelGate {
    @Inject(method="canCraft",at=@At("HEAD"),cancellable=true,remap=false)
    private void whiteLily$fermentationRecipe(@Coerce Object value,RegistryAccess registry,CallbackInfoReturnable<Boolean> callback) {
        if(!(value instanceof Recipe<?> recipe) || !recipe_machine_owner.allowed((BlockEntity)(Object)this,recipe.getId())) callback.setReturnValue(false);
    }
    @Inject(method="craft",at=@At("HEAD"),cancellable=true,remap=false)
    private void whiteLily$fermentationCommit(@Coerce Object value,RegistryAccess registry,CallbackInfo callback) {
        if(!(value instanceof Recipe<?> recipe) || !recipe_machine_owner.allowed((BlockEntity)(Object)this,recipe.getId())) callback.cancel();
    }
}
