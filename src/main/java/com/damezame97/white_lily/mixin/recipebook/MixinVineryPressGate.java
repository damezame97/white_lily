package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.recipe_machine_owner;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.ApplePressBlockEntity",remap=false)
public abstract class MixinVineryPressGate {
    @Inject(method={"canProcessMashing","canProcessFermenting"},at=@At("HEAD"),cancellable=true,remap=false)
    private static void whiteLily$pressRecipe(@Coerce Object machine,@Coerce Object value,CallbackInfoReturnable<Boolean> callback) {
        if(!(value instanceof Recipe<?> recipe) || !recipe_machine_owner.allowed((BlockEntity)machine,recipe.getId())) callback.setReturnValue(false);
    }
    @Inject(method={"processMashing","processFermenting"},at=@At("HEAD"),cancellable=true,remap=false)
    private static void whiteLily$pressCommit(@Coerce Object machine,@Coerce Object value,CallbackInfo callback) {
        if(!(value instanceof Recipe<?> recipe) || !recipe_machine_owner.allowed((BlockEntity)machine,recipe.getId())) callback.cancel();
    }
}
