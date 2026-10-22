package com.damezame97.white_lily.infection;

import com.damezame97.white_lily.Config;
import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.white_lily;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = white_lily.MODID)
public final class infection_events
{
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && !player.isChangingDimension())
        {
            infection_symptom_rules.tick(player, player.server.getTickCount());
            infection_service.tick(player, player.server.getTickCount());
        }
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event)
    {
        Player old = event.getOriginal();
        old.reviveCaps();
        try
        {
            player_variables.infection(old).ifPresent(previous -> player_variables.infection(event.getEntity())
                    .ifPresent(current -> current.copyFrom(previous, event.isWasDeath(), Config.infectionKeepOnDeath)));
        }
        finally { old.invalidateCaps(); }
        // 同步延后至 PlayerRespawnEvent；此时新玩家连接和客户端实体已经就绪。
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide)
        {
            player_variables.infection(player).ifPresent(data -> { data.setPassiveTicks(0); data.symptoms.clear(); });
            infection_symptom_rules.reevaluate(player);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { loaded(event.getEntity()); }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { loaded(event.getEntity()); }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { loaded(event.getEntity()); }

    private static void loaded(Player player)
    {
        infection_network.sync(player, true);
        infection_symptoms.reevaluate(player);
    }
    private infection_events() { }
}
