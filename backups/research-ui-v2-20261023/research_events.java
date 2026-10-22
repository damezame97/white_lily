package com.damezame97.white_lily.research;

import com.damezame97.white_lily.white_lily;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=white_lily.MODID)
public final class research_events
{
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(research_catalog.INSTANCE); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { initialize(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { initialize(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { initialize(event); }
    private static void initialize(PlayerEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            research_capability.get(player).ifPresent(data->{ research_network.close(data); data.lastSnapshot=""; });
            research_service.discover(player); research_service.reconcile(player);
        }
    }
    @SubscribeEvent public static void dataPackSync(OnDatapackSyncEvent event)
    {
        if(event.getPlayer()!=null) research_service.reconcile(event.getPlayer());
        else for(ServerPlayer player:event.getPlayerList().getPlayers()) research_service.reconcile(player);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event)
    {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.level().isClientSide) return;
        long tick=player.level().getGameTime();
        research_network.tickPendingOpen(player,tick);
        if(tick%20==0) research_service.discover(player);
        research_capability.get(player).ifPresent(data->{ if(data.handbookOpen && tick%10==0) research_service.refresh(player,false); });
    }
    private research_events() { }
}
