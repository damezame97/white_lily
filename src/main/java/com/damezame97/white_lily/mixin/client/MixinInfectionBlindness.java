package com.damezame97.white_lily.mixin.client;

import com.damezame97.white_lily.white_lilybuff.mob_effects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版失明雾函数读取独立的感染效果，外部失明的实例和时长完全不变。 */
@Mixin(targets = "net.minecraft.client.renderer.FogRenderer$BlindnessFogFunction")
public abstract class MixinInfectionBlindness
{
    @Inject(method = "getMobEffect", at = @At("RETURN"), cancellable = true)
    private void whiteLily$blindness(CallbackInfoReturnable<MobEffect> callback)
    {
        if (!(Minecraft.getInstance().getCameraEntity() instanceof LivingEntity entity)) return;
        var ours = entity.getEffect(mob_effects.INFECTION_BLINDNESS.get());
        var external = entity.getEffect(MobEffects.BLINDNESS);
        if (ours != null && (external == null || (!external.isInfiniteDuration()
                && (ours.isInfiniteDuration() || ours.getDuration() > external.getDuration()))))
            callback.setReturnValue(mob_effects.INFECTION_BLINDNESS.get());
    }
}
