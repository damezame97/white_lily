package com.damezame97.white_lily.procedures;

import com.damezame97.white_lily.block.white_sleeping_bag;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "white_lily")
public final class sleeping_bag_events {
    @SubscribeEvent
    public static void preventSpawn(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() == null || event.getEntity().getServer() == null) return;
        var level = event.getEntity().getServer().getLevel(event.getSpawnLevel());
        if (level != null && level.getBlockState(event.getNewSpawn()).getBlock() instanceof white_sleeping_bag)
            event.setCanceled(true);
    }
}
