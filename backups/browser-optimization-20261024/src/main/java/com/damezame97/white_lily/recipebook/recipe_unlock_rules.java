package com.damezame97.white_lily.recipebook;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Data-pack rules are authoritative and are never inferred from vanilla recipe awards. */
public final class recipe_unlock_rules extends SimpleJsonResourceReloadListener {
    public static final recipe_unlock_rules INSTANCE = new recipe_unlock_rules();
    public record Grant(Set<ResourceLocation> recipes, Set<String> outputs) { }
    public record Pickup(String item, Grant grant) { }
    private static List<Grant> initial = List.of();
    private static List<Pickup> pickups = List.of();
    private static Map<ResourceLocation,List<Grant>> advancements = Map.of();
    private static long generation;
    private recipe_unlock_rules() { super(new Gson(), "recipe_unlocks"); }
    public static long generation() { return generation; }
    public static List<Grant> initial() { return initial; }
    public static List<Pickup> pickups() { return pickups; }
    public static Map<ResourceLocation,List<Grant>> advancements() { return advancements; }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<Grant> starts = new ArrayList<>(); List<Pickup> finds = new ArrayList<>();
        Map<ResourceLocation,List<Grant>> events = new LinkedHashMap<>();
        for (var entry : files.entrySet()) {
            try {
                JsonObject data = entry.getValue().getAsJsonObject();
                Set<String> outputs=strings(data,"initial_outputs"); outputs.forEach(recipe_unlock_rules::validateSelector);
                starts.add(new Grant(ids(data,"initial"),outputs));
                for (JsonElement value : array(data,"on_pickup")) {
                    JsonObject rule = value.getAsJsonObject();
                    String selector=rule.get("item").getAsString(); validateSelector(selector);
                    finds.add(new Pickup(selector,grant(rule)));
                }
                for (JsonElement value : array(data,"on_advancement")) {
                    JsonObject rule = value.getAsJsonObject();
                    ResourceLocation id = id(rule.get("advancement").getAsString());
                    events.computeIfAbsent(id,key->new ArrayList<>()).add(grant(rule));
                }
            } catch (RuntimeException error) {
                LogUtils.getLogger().error("Rejected recipe unlock rules {}: {}",entry.getKey(),error.getMessage());
            }
        }
        initial = List.copyOf(starts); pickups = List.copyOf(finds);
        events.replaceAll((key,value)->List.copyOf(value)); advancements = Map.copyOf(events); generation++;
    }
    public static boolean matches(String selector, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (selector.startsWith("#")) return stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,id(selector.substring(1))));
        return id(selector).equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }
    public static Set<ResourceLocation> resolve(Grant grant, net.minecraft.world.level.Level level) {
        Set<ResourceLocation> result = new LinkedHashSet<>(grant.recipes());
        if (!grant.outputs().isEmpty()) for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (grant.outputs().stream().anyMatch(selector->matches(selector,output))) result.add(recipe.getId());
        }
        return result;
    }
    private static Grant grant(JsonObject object) {
        Set<String> outputs=strings(object,"outputs"); outputs.forEach(recipe_unlock_rules::validateSelector);
        return new Grant(ids(object,"recipes"),outputs);
    }
    private static void validateSelector(String selector) { id(selector.startsWith("#")?selector.substring(1):selector); }
    private static Set<ResourceLocation> ids(JsonObject object,String key) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String value : strings(object,key)) result.add(id(value)); return Set.copyOf(result);
    }
    private static Set<String> strings(JsonObject object,String key) {
        Set<String> result = new LinkedHashSet<>(); for(JsonElement value:array(object,key)) result.add(value.getAsString()); return Set.copyOf(result);
    }
    private static JsonArray array(JsonObject object,String key) { return object.has(key)?object.getAsJsonArray(key):new JsonArray(); }
    private static ResourceLocation id(String text) {
        ResourceLocation id=ResourceLocation.tryParse(text); if(id==null) throw new JsonParseException("Invalid ID "+text); return id;
    }
}
