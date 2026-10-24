package com.damezame97.white_lily.recipebook;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Predicate;

/** Data-pack rules are authoritative and are never inferred from vanilla recipe awards. */
public final class recipe_unlock_rules extends SimpleJsonResourceReloadListener {
    public static final recipe_unlock_rules INSTANCE = new recipe_unlock_rules();
    public record Grant(Set<ResourceLocation> recipes, Set<String> outputs, Set<String> inputs, Set<String> recipeTypes) { }
    public record Pickup(String item, Grant grant) { }
    public record Conditional(Set<ResourceLocation> seen, Set<ResourceLocation> unlocked,
                              Set<String> unlockedOutputs, Grant grant) {
        /**
         * AND across seen/unlocked, plus at least one unlocked recipe whose real output matches
         * any unlocked_outputs selector. The latter expresses "any wooden door/trapdoor" without
         * hardcoding every wood species or adding a handbook node.
         */
        public boolean matches(Set<ResourceLocation> seenItems, Predicate<ResourceLocation> unlockedRecipe,
                               Predicate<String> unlockedOutput) {
            if (!seenItems.containsAll(seen)) return false;
            for (ResourceLocation id : unlocked) if (!unlockedRecipe.test(id)) return false;
            if (!unlockedOutputs.isEmpty() && unlockedOutputs.stream().noneMatch(unlockedOutput)) return false;
            return true;
        }
    }
    private static List<Grant> initial = List.of();
    private static List<Pickup> pickups = List.of();
    private static Map<ResourceLocation,List<Grant>> advancements = Map.of();
    private static List<Conditional> conditionals = List.of();
    private static long generation;
    private recipe_unlock_rules() { super(new Gson(), "recipe_unlocks"); }
    public static long generation() { return generation; }
    public static List<Grant> initial() { return initial; }
    public static List<Pickup> pickups() { return pickups; }
    public static Map<ResourceLocation,List<Grant>> advancements() { return advancements; }
    public static List<Conditional> conditionals() { return conditionals; }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<Grant> starts = new ArrayList<>(); List<Pickup> finds = new ArrayList<>();
        Map<ResourceLocation,List<Grant>> events = new LinkedHashMap<>();
        List<Conditional> checks = new ArrayList<>();
        for (var entry : files.entrySet()) {
            try {
                JsonObject data = entry.getValue().getAsJsonObject();
                Set<String> outputs=strings(data,"initial_outputs"); outputs.forEach(recipe_unlock_rules::validateSelector);
                starts.add(new Grant(ids(data,"initial"),outputs,Set.of(),Set.of()));
                for (JsonElement value : array(data,"on_pickup")) {
                    JsonObject rule = value.getAsJsonObject();
                    Grant grant=grant(rule);
                    List<String> selectors=new ArrayList<>();
                    if(rule.has("items")) for(JsonElement item:rule.getAsJsonArray("items")) selectors.add(item.getAsString());
                    else selectors.add(rule.get("item").getAsString());
                    for(String selector:selectors) { validateSelector(selector); finds.add(new Pickup(selector,grant)); }
                }
                for (JsonElement value : array(data,"on_advancement")) {
                    JsonObject rule = value.getAsJsonObject();
                    ResourceLocation id = id(rule.get("advancement").getAsString());
                    events.computeIfAbsent(id,key->new ArrayList<>()).add(grant(rule));
                }
                for (JsonElement value : array(data,"conditional")) {
                    JsonObject rule = value.getAsJsonObject();
                    Set<String> unlockedOutputs=strings(rule,"unlocked_outputs");
                    unlockedOutputs.forEach(recipe_unlock_rules::validateSelector);
                    checks.add(new Conditional(ids(rule,"seen"),ids(rule,"unlocked"),unlockedOutputs,grant(rule)));
                }
            } catch (RuntimeException error) {
                LogUtils.getLogger().error("Rejected recipe unlock rules {}: {}",entry.getKey(),error.getMessage());
            }
        }
        initial = List.copyOf(starts); pickups = List.copyOf(finds);
        events.replaceAll((key,value)->List.copyOf(value)); advancements = Map.copyOf(events);
        conditionals = List.copyOf(checks);
        // Stable content signature, not a per-process reload counter: a restart with new
        // rules must still re-evaluate the seen history of existing players.
        List<Map.Entry<ResourceLocation,JsonElement>> ordered=new ArrayList<>(files.entrySet());
        ordered.sort(Comparator.comparing(entry->entry.getKey().toString()));
        long signature=1125899906842597L;
        for(Map.Entry<ResourceLocation,JsonElement> entry:ordered) {
            signature=signature*31+entry.getKey().toString().hashCode();
            signature=signature*31+entry.getValue().toString().hashCode();
        }
        generation=signature;
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
            if (grant.outputs().stream().noneMatch(selector->matches(selector,output))) continue;
            if (!grant.inputs().isEmpty() && recipe.getIngredients().stream().noneMatch(ingredient->inputMatches(ingredient,grant.inputs()))) continue;
            if (!grant.recipeTypes().isEmpty() && !grant.recipeTypes().contains(recipeType(recipe))) continue;
            result.add(recipe.getId());
        }
        return result;
    }
    private static boolean inputMatches(Ingredient ingredient,Set<String> inputs) {
        for (ItemStack candidate : ingredient.getItems())
            if (inputs.stream().anyMatch(selector->matches(selector,candidate))) return true;
        return false;
    }
    private static String recipeType(Recipe<?> recipe) {
        ResourceLocation id=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return id==null?"":id.getPath();
    }
    private static Grant grant(JsonObject object) {
        Set<String> outputs=strings(object,"outputs"); outputs.forEach(recipe_unlock_rules::validateSelector);
        Set<String> inputs=strings(object,"inputs"); inputs.forEach(recipe_unlock_rules::validateSelector);
        return new Grant(ids(object,"recipes"),outputs,inputs,strings(object,"recipe_types"));
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
