package com.damezame97.white_lily.machine;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/** Device-local views. The global recipe manager is NEVER filtered or augmented. */
public final class machine_rules {
    private static final Map<ResourceLocation, machine_adapter> ADAPTERS = new LinkedHashMap<>();
    public static final List<ResourceLocation> VANILLA_DEVICES = List.of(id("minecraft:furnace"), id("minecraft:smoker"), id("minecraft:blast_furnace"));
    private static final AtomicLong SERIAL = new AtomicLong();
    static { register(new vanilla_cooking_adapter()); }
    public static synchronized void register(machine_adapter adapter) {
        if (ADAPTERS.putIfAbsent(adapter.id(), adapter) != null) throw new IllegalArgumentException("Duplicate adapter " + adapter.id());
    }
    public record Copy(ResourceLocation source, ResourceLocation id, int ticks, boolean removeSource) { }
    public record Rule(ResourceLocation file, ResourceLocation device, ResourceLocation adapter,
                       boolean whitelist, Set<ResourceLocation> allow, Set<ResourceLocation> deny, List<Copy> copies) { }
    public record State(List<Rule> rules, Map<ResourceLocation,List<Recipe<?>>> tables,
                        Map<ResourceLocation,String> report, long revision, boolean healthy) { }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static Map<ResourceLocation,List<Recipe<?>>> emptyTables(List<Rule> rules) {
        Map<ResourceLocation,List<Recipe<?>>> result = new LinkedHashMap<>();
        VANILLA_DEVICES.forEach(d -> result.put(d, List.of())); rules.forEach(r -> result.put(r.device(), List.of()));
        return result;
    }
    private static final State EMPTY = new State(List.of(), Map.copyOf(emptyTables(List.of())), Map.of(), 0, false);
    public static State state(RecipeManager manager) { return ((machine_state_access) manager).whiteLily$machines(); }
    public static State empty() { return EMPTY; }
    public static boolean cooking(Recipe<?> recipe) {
        return recipe.getType() == RecipeType.SMELTING || recipe.getType() == RecipeType.SMOKING || recipe.getType() == RecipeType.BLASTING;
    }
    public static ResourceLocation device(BlockEntity machine) {
        var vanilla = ADAPTERS.get(id("white_lily:vanilla_cooking"));
        for (var d : VANILLA_DEVICES) if (vanilla.matches(machine,d)) return d;
        if (machine.getLevel() != null) for (var r : state(machine.getLevel().getRecipeManager()).rules()) {
            var adapter = ADAPTERS.get(r.adapter());
            if (adapter != null && adapter.matches(machine,r.device())) return r.device();
        }
        return null;
    }
    public static List<Recipe<?>> recipes(RecipeManager manager, ResourceLocation device) {
        return state(manager).tables().getOrDefault(device,List.of());
    }
    public static Optional<AbstractCookingRecipe> findCooking(BlockEntity machine, Level level) {
        var device = device(machine);
        if (device == null || !(machine instanceof net.minecraft.world.Container input)) return Optional.empty();
        return recipes(level.getRecipeManager(),device).stream().filter(r -> r instanceof AbstractCookingRecipe)
                .map(r -> (AbstractCookingRecipe) r).filter(r -> r.matches(input,level)).findFirst();
    }
    public static boolean allowed(BlockEntity machine, ResourceLocation recipe) {
        var device = device(machine);
        return device == null || machine.getLevel() != null && recipes(machine.getLevel().getRecipeManager(),device).stream().anyMatch(r -> r.getId().equals(recipe));
    }
    private static Set<ResourceLocation> ids(JsonObject json,String key) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (json.has(key)) for (var e : json.getAsJsonArray(key)) result.add(id(e.getAsString())); return Set.copyOf(result);
    }
    public static Rule parse(ResourceLocation file, JsonObject json) {
        String mode = json.has("mode") ? json.get("mode").getAsString() : "whitelist";
        if (!mode.equals("whitelist") && !mode.equals("blacklist")) throw new IllegalArgumentException("Invalid mode " + mode);
        List<Copy> copies = new ArrayList<>();
        if (json.has("copies")) for (var e : json.getAsJsonArray("copies")) {
            var c = e.getAsJsonObject(); int time = c.has("cooking_time") ? c.get("cooking_time").getAsInt() : 0;
            if (time < 0) throw new IllegalArgumentException("Negative cooking_time");
            copies.add(new Copy(id(c.get("source").getAsString()),id(c.get("id").getAsString()),time,
                    c.has("remove_source") && c.get("remove_source").getAsBoolean()));
        }
        return new Rule(file,id(json.get("device").getAsString()),id(json.get("adapter").getAsString()),
                mode.equals("whitelist"),ids(json,"allow"),ids(json,"deny"),List.copyOf(copies));
    }
    public static State reload(ResourceManager resources, Collection<Recipe<?>> original) {
        List<Rule> rules = new ArrayList<>();
        try {
            for (var e : new TreeMap<>(resources.listResources("machine_rules",p -> p.getPath().endsWith(".json"))).entrySet()) {
                try (var reader = e.getValue().openAsReader()) {
                    var json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("required_mod") && !ModList.get().isLoaded(json.get("required_mod").getAsString())) continue;
                    rules.add(parse(e.getKey(),json));
                } catch (Exception error) { throw new IllegalArgumentException(e.getKey() + ": " + error.getMessage(),error); }
            }
            return build(original,rules);
        } catch (RuntimeException error) { return closed(rules,error); }
    }
    public static State rebuild(Collection<Recipe<?>> original, State previous) {
        if (!previous.healthy()) return previous;
        try { return build(original,previous.rules()); } catch (RuntimeException e) { return closed(previous.rules(),e); }
    }
    private static State closed(List<Rule> rules, RuntimeException error) {
        LogUtils.getLogger().error("White Lily device policy rejected; enrolled devices closed, global recipes unchanged: {}",error.getMessage(),error);
        return new State(List.copyOf(rules),Map.copyOf(emptyTables(rules)),Map.of(id("white_lily:error"),String.valueOf(error.getMessage())),SERIAL.incrementAndGet(),false);
    }
    /** Devices sharing a RecipeType still receive independent policies. */
    public static State build(Collection<Recipe<?>> original, List<Rule> rules) {
        Map<ResourceLocation,Recipe<?>> sources = new LinkedHashMap<>(); for (var r : original) sources.put(r.getId(),r);
        Map<ResourceLocation,List<Recipe<?>>> tables = emptyTables(rules);
        Map<ResourceLocation,String> report = new LinkedHashMap<>();
        Set<ResourceLocation> devices = new HashSet<>(), privateIds = new HashSet<>(), removed = new HashSet<>();
        for (var rule : rules) {
            if (!devices.add(rule.device())) throw new IllegalArgumentException("Duplicate policy for device " + rule.device());
            var adapter = ADAPTERS.get(rule.adapter());
            if (adapter == null) throw new IllegalArgumentException(rule.file() + ": unknown adapter " + rule.adapter());
            var type = adapter.type(rule.device());
            Map<ResourceLocation,Recipe<?>> candidates = new LinkedHashMap<>();
            sources.forEach((key,r) -> { if (r.getType() == type) candidates.put(key,r); });
            Set<ResourceLocation> copies = new HashSet<>();
            for (var c : rule.copies()) {
                var source = sources.get(c.source());
                if (source == null) throw new IllegalArgumentException(rule.file() + ": missing source " + c.source());
                if (sources.containsKey(c.id()) || !privateIds.add(c.id())) throw new IllegalArgumentException("Duplicate copy ID " + c.id());
                candidates.put(c.id(),adapter.copy(rule.device(),c.id(),source,c.ticks())); copies.add(c.id());
                if (c.removeSource()) removed.add(c.source());
            }
            for (var allow : rule.allow()) if (!candidates.containsKey(allow)) throw new IllegalArgumentException(rule.file() + ": missing/wrong-type allow recipe " + allow);
            List<Recipe<?>> selected = new ArrayList<>();
            candidates.forEach((key,r) -> { if (!rule.deny().contains(key) && (!rule.whitelist() || rule.allow().contains(key) || copies.contains(key))) selected.add(r); });
            selected.sort(Comparator.comparing(r -> r.getId().toString())); tables.put(rule.device(),List.copyOf(selected));
        }
        tables.replaceAll((d,list) -> list.stream().filter(r -> !removed.contains(r.getId())).toList());
        for (var rule : rules) report.put(rule.device(),rule.file() + " [" + (rule.whitelist()?"whitelist":"blacklist") + "] " + tables.get(rule.device()).size() + " recipes");
        return new State(List.copyOf(rules),Map.copyOf(tables),Map.copyOf(report),SERIAL.incrementAndGet(),true);
    }
}
