package com.damezame97.white_lily.client;

import com.damezame97.white_lily.infection.infection_mana;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 单独的客户端类，避免 Mixin 在专用服务器分析到 Minecraft 客户端类型。 */
public final class infection_mana_display
{
    public static void apply(CallbackInfoReturnable<Integer> callback)
    {
        var client = Minecraft.getInstance();
        if (client.isSameThread()) callback.setReturnValue(infection_mana.cost(client.player, callback.getReturnValue()));
    }
    private infection_mana_display() { }
}
