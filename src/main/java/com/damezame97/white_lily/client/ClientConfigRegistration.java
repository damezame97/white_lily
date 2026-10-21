package com.damezame97.white_lily.client;

import com.damezame97.white_lily.white_lily;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Exposes the configuration screen through Forge's mod list on the client. */
@Mod.EventBusSubscriber(modid = white_lily.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientConfigRegistration
{
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(SpecialWindowConfigScreen::new));
    }

    private ClientConfigRegistration()
    {
    }
}
