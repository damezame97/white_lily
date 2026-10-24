package com.damezame97.white_lily.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * One edge-triggered dispatcher for the mod's KeyMappings.
 *
 * <p>Holding a key does not repeat. Browser actions are delegated to the browser-owned
 * bridge; the handbook action uses the existing server-authoritative open request.</p>
 */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT)
public final class white_lily_key_handler
{
    private static final Set<KeyMapping> DOWN = Collections.newSetFromMap(new IdentityHashMap<>());

    @SubscribeEvent
    public static void key(InputEvent.Key event)
    {
        poll();
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton event)
    {
        poll();
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event)
    {
        // Opening a screen while a key is held must not make that held key fire on the next
        // unrelated input event. Suppress it until the key is released and pressed again.
        syncState();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        DOWN.clear();
    }

    private static void syncState()
    {
        for (KeyMapping mapping : white_lily_keybinds.ALL)
        {
            if (mapping.isDown()) DOWN.add(mapping);
            else DOWN.remove(mapping);
        }
    }

    private static void poll()
    {
        for (KeyMapping mapping : white_lily_keybinds.ALL)
        {
            boolean down = mapping.isDown();
            boolean wasDown = DOWN.contains(mapping);
            if (down && !wasDown) trigger(mapping);
            if (down) DOWN.add(mapping);
            else DOWN.remove(mapping);
        }
    }

    private static void trigger(KeyMapping mapping)
    {
        // Browser shortcuts are dispatched by recipe_browser's ScreenEvent integration (which
        // also owns mouse-priority and the list context). Dispatching them here as well would
        // execute every browser action twice. This module only owns the independent handbook key.
        if (mapping != white_lily_keybinds.OPEN_HANDBOOK) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() != null && minecraft.player.isAlive()
                && !research_client.isOpening())
            research_client.openFromItem();
    }

    private white_lily_key_handler() { }
}
