package com.damezame97.white_lily.mixin.research;

import com.damezame97.white_lily.research.research_crafting_gate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原版已经存在的映射方法中拦截，避免新增override在正式包中丢失映射。 */
@Mixin(Slot.class)
public abstract class MixinResearchResultPickup
{
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void whiteLily$researchPickup(Player player, CallbackInfoReturnable<Boolean> callback)
    {
        if (!research_crafting_gate.canTake((Slot)(Object)this, player)) callback.setReturnValue(false);
    }
}
