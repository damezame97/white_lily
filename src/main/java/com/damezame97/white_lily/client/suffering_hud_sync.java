package com.damezame97.white_lily.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import com.damezame97.white_lily.capability.suffering_capability;

public final class suffering_hud_sync {
    public static void apply(UUID id, float amount) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getUUID().equals(id)) {
            suffering_capability.get(player).ifPresent(data ->
                    data.clientPending = Float.isFinite(amount) ? Math.max(0, amount) : 0);
        }
    }
}
