package com.damezame97.white_lily.client;

import javax.annotation.Nullable;

/**
 * Stable action boundary between the key-binding module and the recipe browser.
 *
 * <p>The browser owns its screen/panel/query state, so it installs a handler once. The
 * key module never reaches into private browser fields. Until the browser handler is
 * installed, browser actions are ignored; the handbook binding is independent.</p>
 */
public final class recipe_browser_key_bridge
{
    public enum Action
    {
        TOGGLE,
        SEARCH,
        RECIPES,
        USES,
        FAVORITE,
        BACK,
        PREVIOUS,
        NEXT
    }

    @FunctionalInterface
    public interface Handler
    {
        boolean handle(Action action);
    }

    @Nullable private static Handler handler;

    public static void install(Handler value)
    {
        handler = value;
    }

    public static void clear()
    {
        handler = null;
    }

    public static boolean available()
    {
        return handler != null;
    }

    public static boolean handle(Action action)
    {
        Handler current = handler;
        return current != null && action != null && current.handle(action);
    }

    private recipe_browser_key_bridge() { }
}
