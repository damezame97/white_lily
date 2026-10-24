package com.damezame97.white_lily.client;

import com.damezame97.white_lily.recipebook.browser_cheat_network;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client-only UI state for the administrator test mode. The server is authoritative for
 * permission and for every give request; this class never creates an item itself.
 */
public final class browser_cheat_client
{
    private static boolean allowed;
    private static boolean itemGive;
    private static boolean showAll;
    private static long revision;
    private static long indexRevision;

    public static void acceptPermission(boolean value, long serverRevision)
    {
        boolean wasAllowed = allowed;
        allowed = value;
        if (!allowed)
        {
            itemGive = false;
            showAll = false;
        }
        revision = Math.max(revision + 1, serverRevision);
        if (wasAllowed != allowed || !allowed)
        {
            indexRevision++;
            recipe_browser_index.invalidate();
        }
    }

    public static void acceptResult(int requestId, browser_cheat_network.Result result)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        String key = "screen.white_lily.recipe_browser.cheat.result." + result.name().toLowerCase(java.util.Locale.ROOT);
        minecraft.player.displayClientMessage(Component.translatable(key), true);
    }

    public static boolean allowed() { return allowed; }
    public static boolean itemGive() { return allowed && itemGive; }
    public static boolean showAll() { return allowed && showAll; }
    public static long revision() { return revision; }
    public static long indexRevision() { return indexRevision; }

    public static void toggleItemGive()
    {
        if (!allowed) return;
        itemGive = !itemGive;
        revision++;
    }

    public static void toggleShowAll()
    {
        if (!allowed) return;
        showAll = !showAll;
        revision++;
        indexRevision++;
        recipe_browser_index.invalidate();
    }

    public static void requestPermission()
    {
        browser_cheat_network.requestPermission();
    }

    public static void requestGive(ItemStack stack, boolean fullStack)
    {
        if (!itemGive() || stack == null || stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.containerMenu == null) return;
        browser_cheat_network.requestGive(stack.copyWithCount(1), fullStack, minecraft.player.containerMenu.containerId);
    }

    /** Any left-mouse favorite binding is shadowed by take mode, including Shift+Left. */
    public static boolean favoriteConflictsWithTake()
    {
        return itemGive()
                && white_lily_keybinds.BROWSER_FAVORITE.getKey().getType() == InputConstants.Type.MOUSE
                && white_lily_keybinds.BROWSER_FAVORITE.getKey().getValue() == 0;
    }

    public static void reset()
    {
        allowed = false;
        itemGive = false;
        showAll = false;
        revision++;
        indexRevision++;
        recipe_browser_index.invalidate();
    }

    private browser_cheat_client() { }
}
