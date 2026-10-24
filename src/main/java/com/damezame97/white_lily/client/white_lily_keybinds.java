package com.damezame97.white_lily.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The mod's public key mappings. This class is client-only and is registered on the
 * mod event bus; a dedicated server never loads it.
 */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class white_lily_keybinds
{
    public static final String CATEGORY_BROWSER = "key.categories.white_lily.recipe_browser";
    public static final String CATEGORY_HANDBOOK = "key.categories.white_lily.research_handbook";

    public static final KeyMapping BROWSER_TOGGLE = mapping("key.white_lily.browser.toggle",
            KeyConflictContext.GUI, KeyModifier.CONTROL, GLFW.GLFW_KEY_O, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_SEARCH = mapping("key.white_lily.browser.search",
            KeyConflictContext.GUI, KeyModifier.CONTROL, GLFW.GLFW_KEY_F, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_RECIPES = mapping("key.white_lily.browser.recipes",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_R, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_USES = mapping("key.white_lily.browser.uses",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_U, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_FAVORITE = mapping("key.white_lily.browser.favorite",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_A, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_BACK = mapping("key.white_lily.browser.back",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_PREVIOUS = mapping("key.white_lily.browser.previous",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_LEFT, CATEGORY_BROWSER);
    public static final KeyMapping BROWSER_NEXT = mapping("key.white_lily.browser.next",
            KeyConflictContext.GUI, KeyModifier.NONE, GLFW.GLFW_KEY_RIGHT, CATEGORY_BROWSER);
    public static final KeyMapping OPEN_HANDBOOK = mapping("key.white_lily.handbook.open",
            KeyConflictContext.IN_GAME, KeyModifier.NONE, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_HANDBOOK);

    public static final List<KeyMapping> ALL = List.of(
            BROWSER_TOGGLE, BROWSER_SEARCH, BROWSER_RECIPES, BROWSER_USES, BROWSER_FAVORITE,
            BROWSER_BACK, BROWSER_PREVIOUS, BROWSER_NEXT, OPEN_HANDBOOK);

    private static KeyMapping mapping(String name, KeyConflictContext context, KeyModifier modifier,
                                      int keyCode, String category)
    {
        return new KeyMapping(name, context, modifier, InputConstants.Type.KEYSYM, keyCode, category);
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event)
    {
        for (KeyMapping mapping : ALL) event.register(mapping);
    }

    private white_lily_keybinds() { }
}
