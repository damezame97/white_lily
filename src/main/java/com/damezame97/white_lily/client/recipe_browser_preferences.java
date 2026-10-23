package com.damezame97.white_lily.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Favorites are presentation preferences; they never grant recipe access. */
final class recipe_browser_preferences
{
    private static final Set<String> FAVORITES = new HashSet<>();
    private static boolean loaded;
    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("white_lily-recipe-favorites.json"); }
    private static void load()
    {
        if (loaded) return;
        loaded = true;
        try
        {
            if (Files.isRegularFile(file()))
                for (JsonElement element : new Gson().fromJson(Files.readString(file(), StandardCharsets.UTF_8), JsonArray.class))
                    if (element.isJsonPrimitive()) FAVORITES.add(element.getAsString());
        }
        catch (Exception exception) { com.mojang.logging.LogUtils.getLogger().warn("Cannot read recipe favorites", exception); }
    }
    static boolean contains(ItemStack stack)
    { load(); return FAVORITES.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); }
    static void toggle(ItemStack stack)
    {
        load();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!FAVORITES.remove(id)) FAVORITES.add(id);
        try
        {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new Gson().toJson(FAVORITES), StandardCharsets.UTF_8);
        }
        catch (Exception exception) { com.mojang.logging.LogUtils.getLogger().warn("Cannot save recipe favorites", exception); }
    }
    private recipe_browser_preferences() { }
}
