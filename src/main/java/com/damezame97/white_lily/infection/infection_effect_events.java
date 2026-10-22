package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.white_lily;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class infection_effect_events
{
    @SubscribeEvent
    public static void added(MobEffectEvent.Added event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || com.damezame97.white_lily.capability.player_variables.infection(player).isEmpty()) return;
        var state = infection_symptom_rules.state(player);
        if (state == null || state.changingEffects) return;
        state.dirty = true;
        var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(event.getEffectInstance().getEffect());
        if (key != null && key.getNamespace().equals("white_lily") && infection_symptom_rules.timedIds().contains(key.getPath()))
        {
            // Added 在原版合并效果之前触发。用副本模拟原版合并，不能把一次无效的
            // 短时重加写成更短的私有计时，也不能把原来的无限效果截断。
            var previous = player.getEffect(event.getEffectInstance().getEffect());
            var merged = previous == null ? new net.minecraft.world.effect.MobEffectInstance(event.getEffectInstance())
                    : new net.minecraft.world.effect.MobEffectInstance(previous);
            if (previous != null) merged.update(event.getEffectInstance());
            state.set(key.getPath(), merged.getDuration());
        }
    }

    @SubscribeEvent
    public static void applicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && event.getEffectInstance().getEffect() == infection_symptom_rules.effect("infection_blindness")
                && infection_symptom_rules.has(player, "brief_lucidity"))
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    @SubscribeEvent
    public static void expired(MobEffectEvent.Expired event)
    {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide)
        {
            var state = infection_symptom_rules.state(player);
            if (state != null && !state.changingEffects) state.dirty = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove event)
    {
        if (!event.isCanceled() && event.getEntity() instanceof Player player)
            infection_symptom_rules.removed(player, event.getEffect());
    }
    private infection_effect_events() { }
}
