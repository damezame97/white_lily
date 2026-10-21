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
        if (state.changingEffects) return;
        var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(event.getEffectInstance().getEffect());
        if (key != null && key.getNamespace().equals("white_lily") && infection_symptom_rules.timedIds().contains(key.getPath()))
            state.set(key.getPath(), Math.max(1, event.getEffectInstance().getDuration()));
    }

    @SubscribeEvent
    public static void applicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && event.getEffectInstance().getEffect() == com.damezame97.white_lily.white_lilybuff.mob_effects.INFECTION_BLINDNESS.get()
                && player.hasEffect(com.damezame97.white_lily.white_lilybuff.mob_effects.BRIEF_LUCIDITY.get()))
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove event)
    {
        if (!event.isCanceled() && event.getEntity() instanceof Player player)
            infection_symptom_rules.removed(player, event.getEffect());
    }
    private infection_effect_events() { }
}
