package com.damezame97.white_lily.client;

import com.damezame97.white_lily.capability.player_variables;
import com.damezame97.white_lily.infection.infection_network;
import net.minecraft.client.Minecraft;

/** 只有客户端线程处理服务端快照；没有 HUD，也不向服务端回传数据。 */
public final class infection_client_sync
{
    public static void apply(infection_network.Snapshot snapshot)
    {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player_variables.infection(player).ifPresent(data -> {
            data.setUnits(snapshot.units());
            data.setPassiveTicks(snapshot.passiveTicks());
        });
    }
    private infection_client_sync() { }
}
