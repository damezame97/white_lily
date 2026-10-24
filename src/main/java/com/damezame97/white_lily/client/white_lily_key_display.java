package com.damezame97.white_lily.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** Uses the live KeyMapping names (including modifiers and unbound state); never reads config files. */
public final class white_lily_key_display
{
    public static Component key(KeyMapping mapping)
    {
        return mapping.getTranslatedKeyMessage().copy();
    }

    public static Component hint(String translationKey, KeyMapping... mappings)
    {
        Object[] keys = Arrays.stream(mappings).map(KeyMapping::getTranslatedKeyMessage).toArray();
        return Component.translatable(translationKey, keys);
    }

    private white_lily_key_display() { }
}
