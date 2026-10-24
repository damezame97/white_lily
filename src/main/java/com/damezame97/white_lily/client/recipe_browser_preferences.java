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
    private static com.damezame97.white_lily.recipebook.browser_order.Mode sort=com.damezame97.white_lily.recipebook.browser_order.Mode.DEFAULT;
    static com.damezame97.white_lily.recipebook.browser_order.Mode sort(){load();return sort;}
    private static int itemScale=100,recipeScale=100;
    static int itemScale(){load();return itemScale;}
    static int recipeScale(){load();return recipeScale;}
    static void itemScale(int value){load();itemScale=com.damezame97.white_lily.recipebook.browser_layout.percent(value);save();}
    static void recipeScale(int value){load();recipeScale=com.damezame97.white_lily.recipebook.browser_layout.percent(value);save();}
    static void nextSort(){load();sort=sort.next();save();}
    private static void save(){try{var json=new com.google.gson.JsonObject();json.addProperty("sort",sort.name());json.addProperty("itemScale",itemScale);json.addProperty("recipeScale",recipeScale);Files.createDirectories(FMLPaths.CONFIGDIR.get());Files.writeString(FMLPaths.CONFIGDIR.get().resolve("white_lily-recipe-browser.json"),new Gson().toJson(json),StandardCharsets.UTF_8);}catch(Exception e){com.mojang.logging.LogUtils.getLogger().warn("Cannot save browser preferences",e);}}
    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("white_lily-recipe-favorites.json"); }
    private static void load()
    {
        if (loaded) return;
        loaded = true;
        try {Path prefs=FMLPaths.CONFIGDIR.get().resolve("white_lily-recipe-browser.json");if(Files.isRegularFile(prefs)){var json=com.google.gson.JsonParser.parseString(Files.readString(prefs)).getAsJsonObject();if(json.has("sort"))sort=com.damezame97.white_lily.recipebook.browser_order.Mode.valueOf(json.get("sort").getAsString());if(json.has("itemScale"))itemScale=com.damezame97.white_lily.recipebook.browser_layout.percent(json.get("itemScale").getAsInt());if(json.has("recipeScale"))recipeScale=com.damezame97.white_lily.recipebook.browser_layout.percent(json.get("recipeScale").getAsInt());}}
        catch(Exception e){sort=com.damezame97.white_lily.recipebook.browser_order.Mode.DEFAULT;}
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
