package com.damezame97.white_lily.client;

import com.damezame97.white_lily.processing.processing_registry;
import com.damezame97.white_lily.white_lily;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 仅客户端注册 Screen，公共菜单与方块实体不引用客户端类。 */
@Mod.EventBusSubscriber(modid = white_lily.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class processing_client
{
    private processing_client() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> MenuScreens.register(processing_registry.TABLE_MENU.get(), processing_screen::new));
    }
}
