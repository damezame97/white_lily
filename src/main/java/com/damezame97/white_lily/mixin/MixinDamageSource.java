package com.damezame97.white_lily.mixin;

import com.damezame97.white_lily.white_lilydamage.depletion;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把「枯竭伤害」的死亡提示换成 17 条里随机一条。
 *
 * <p>{@link DamageSource#getLocalizedDeathMessage(LivingEntity)} 是原版算死亡播报的地方，
 * {@code CombatTracker#getDeathMessage()} 会调它，而服务端把结果组件原样发给客户端当死亡界面文本。
 * 所以在这里换掉，服务端播报、日志、死亡界面三处显示的都是同一条。</p>
 *
 * <p>只对 {@code white_lily:depletion} 生效，其它伤害原样交给原版。</p>
 */
@Mixin(DamageSource.class)
public abstract class MixinDamageSource
{
    @Inject(method = "getLocalizedDeathMessage", at = @At("HEAD"), cancellable = true)
    private void whiteLily$depletionDeathMessage(LivingEntity victim, CallbackInfoReturnable<Component> cir)
    {
        DamageSource self = (DamageSource) (Object) this;
        if (self.is(depletion.KEY))
        {
            cir.setReturnValue(depletion.randomDeathMessage(victim));
        }
    }
}
