package com.damezame97.white_lily.mixin.recipebook;
import com.damezame97.white_lily.recipebook.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Pauses only the brewing stand, before consuming its fuel or ingredients. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class MixinBrewingRecipeGate {
    @Inject(method="serverTick",at=@At("HEAD"),cancellable=true)
    private static void whiteLily$lockedBrewing(Level level,BlockPos pos,BlockState state,BrewingStandBlockEntity stand,CallbackInfo callback) {
        if(!recipe_machine_owner.allowed(stand,recipe_menu_gate.operation("brewing"))) callback.cancel();
    }
}
