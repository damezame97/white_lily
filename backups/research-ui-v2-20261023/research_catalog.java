package com.damezame97.white_lily.research;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** 完整配置仅在服务端资源管理器加载；客户端只接收已揭示节点的裁剪快照。 */
public final class research_catalog extends SimpleJsonResourceReloadListener
{
    public record Chapter(ResourceLocation id, String title) { }
    public record Category(ResourceLocation id, String title, boolean open, List<Chapter> chapters) { }
    public record Material(Item item, int count) { }
    public record Node(ResourceLocation id, ResourceLocation category, ResourceLocation chapter, Item icon,
                       String name, String intro, String hint, int x, int y, List<ResourceLocation> prerequisites,
                       boolean rootVisible, String condition, Item target, List<Material> materials,
                       List<ResourceLocation> recipes, List<String> archive) { }
    public static final research_catalog INSTANCE = new research_catalog();
    private static volatile List<Category> categories = List.of();
    private static volatile Map<ResourceLocation, Node> nodes = Map.of();
    private static volatile Map<Item, List<Node>> discoveries = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> recipeOwners = Map.of();
    private static volatile long generation;
    public research_catalog() { super(new Gson(), "research"); }
    public static List<Category> categories() { return categories; }
    public static Map<ResourceLocation, Node> nodes() { return nodes; }
    public static Map<Item, List<Node>> discoveries() { return discoveries; }
    public static Map<ResourceLocation, ResourceLocation> recipeOwners() { return recipeOwners; }
    public static long generation() { return generation; }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        try
        {
            Parsed parsed = parse(files);
            categories = parsed.categories(); nodes = parsed.nodes();
            Map<Item, List<Node>> index = new HashMap<>();
            Map<ResourceLocation, ResourceLocation> owners = new LinkedHashMap<>();
            for (Node node : nodes.values())
            {
                if (node.condition().equals("obtain")) index.computeIfAbsent(node.target(), key -> new ArrayList<>()).add(node);
                for (ResourceLocation recipe : node.recipes())
                    if (owners.putIfAbsent(recipe, node.id()) != null) throw new JsonParseException("Duplicate gated recipe " + recipe);
            }
            index.replaceAll((key, value) -> List.copyOf(value));
            discoveries = Map.copyOf(index); recipeOwners = Map.copyOf(owners); generation++;
            LogUtils.getLogger().info("White Lily research: loaded {} nodes from {} files", nodes.size(), files.size());
        }
        catch (RuntimeException exception)
        {
            // 不发布半棵损坏研究树；第一条花茶权限由服务层兜底为锁定。
            categories = List.of(); nodes = Map.of(); discoveries = Map.of(); recipeOwners = Map.of(); generation++;
            LogUtils.getLogger().error("White Lily research configuration rejected: {}", exception.getMessage(), exception);
        }
    }
    public record Parsed(List<Category> categories, Map<ResourceLocation, Node> nodes) { }
    public static Parsed parse(Map<ResourceLocation, JsonElement> files)
    {
        Map<ResourceLocation, Category> cats = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> chapters = new HashMap<>();
        Map<ResourceLocation, Node> all = new LinkedHashMap<>();
        for (ResourceLocation file : files.keySet().stream().sorted().toList())
        {
            try
            {
                JsonObject root = GsonHelper.convertToJsonObject(files.get(file), file.toString());
                for (JsonElement value : GsonHelper.getAsJsonArray(root, "categories"))
                {
                    JsonObject c = value.getAsJsonObject(); ResourceLocation id = id(c, "id");
                    List<Chapter> list = new ArrayList<>();
                    for (JsonElement child : GsonHelper.getAsJsonArray(c, "chapters", new JsonArray()))
                    {
                        JsonObject chapter = child.getAsJsonObject(); ResourceLocation chapterId = id(chapter, "id");
                        if (chapters.putIfAbsent(chapterId, id) != null) throw new JsonParseException("Duplicate chapter " + chapterId);
                        list.add(new Chapter(chapterId, text(chapter, "title")));
                    }
                    if (cats.putIfAbsent(id, new Category(id, text(c, "title"), GsonHelper.getAsBoolean(c, "open", false), List.copyOf(list))) != null)
                        throw new JsonParseException("Duplicate category " + id);
                }
                for (JsonElement value : GsonHelper.getAsJsonArray(root, "nodes"))
                {
                    JsonObject n = value.getAsJsonObject(); ResourceLocation id = id(n, "id");
                    String condition = text(n, "condition");
                    if (!condition.equals("obtain") && !condition.equals("submit")) throw new JsonParseException("Unknown condition " + condition + " for " + id);
                    List<Material> materials = new ArrayList<>();
                    for (JsonElement entry : GsonHelper.getAsJsonArray(n, "materials", new JsonArray()))
                    {
                        JsonObject m = entry.getAsJsonObject(); int count = GsonHelper.getAsInt(m, "count");
                        if (count < 1 || count > 2304) throw new JsonParseException("Invalid count for " + id);
                        materials.add(new Material(item(m, "item"), count));
                    }
                    if (condition.equals("submit") && materials.isEmpty()) throw new JsonParseException("Empty submit materials for " + id);
                    int x = GsonHelper.getAsInt(n, "x"), y = GsonHelper.getAsInt(n, "y");
                    if (Math.abs((long) x) > 100000 || Math.abs((long) y) > 100000) throw new JsonParseException("Invalid coordinates for " + id);
                    Node node = new Node(id, id(n,"category"), id(n,"chapter"), item(n,"icon"), text(n,"name"), text(n,"intro"),
                            text(n,"hint"), x, y, ids(n,"prerequisites"), GsonHelper.getAsBoolean(n,"root_visible",false), condition,
                            condition.equals("obtain") ? item(n,"target") : Items.AIR, List.copyOf(materials), ids(n,"recipes"), texts(n,"archive"));
                    if (all.putIfAbsent(id, node) != null) throw new JsonParseException("Duplicate node " + id);
                }
            }
            catch (RuntimeException exception) { throw new JsonParseException("research/" + file + ": " + exception.getMessage(), exception); }
        }
        Set<ResourceLocation> visiting = new HashSet<>(), visited = new HashSet<>();
        Map<ResourceLocation,ResourceLocation> owners=new HashMap<>();
        for (Node node : all.values())
        {
            if (!cats.containsKey(node.category()) || !node.category().equals(chapters.get(node.chapter())))
                throw new JsonParseException("Missing category/chapter for " + node.id());
            for (ResourceLocation parent : node.prerequisites()) if (!all.containsKey(parent)) throw new JsonParseException("Missing prerequisite " + parent + " for " + node.id());
            for(ResourceLocation recipe:node.recipes()) if(owners.putIfAbsent(recipe,node.id())!=null)
                throw new JsonParseException("Duplicate gated recipe " + recipe + " for " + node.id());
            cycle(node.id(), all, visiting, visited);
        }
        return new Parsed(List.copyOf(cats.values()), Collections.unmodifiableMap(all));
    }
    private static void cycle(ResourceLocation id, Map<ResourceLocation, Node> all, Set<ResourceLocation> visiting, Set<ResourceLocation> visited)
    {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new JsonParseException("Cyclic research prerequisite at " + id);
        for (ResourceLocation parent : all.get(id).prerequisites())
        {
            if (!all.containsKey(parent)) throw new JsonParseException("Missing prerequisite " + parent + " for " + id);
            cycle(parent, all, visiting, visited);
        }
        visiting.remove(id); visited.add(id);
    }
    private static Item item(JsonObject object, String key)
    {
        ResourceLocation id = id(object, key);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) throw new JsonParseException("Invalid item " + id);
        return item;
    }
    private static String text(JsonObject object, String key)
    { String text = GsonHelper.getAsString(object,key); if (text.isBlank() || text.length()>256) throw new JsonParseException("Invalid " + key); return text; }
    private static ResourceLocation id(JsonObject object, String key)
    { ResourceLocation id = ResourceLocation.tryParse(text(object,key)); if (id==null) throw new JsonParseException("Invalid ID " + key); return id; }
    private static List<ResourceLocation> ids(JsonObject object, String key)
    {
        List<ResourceLocation> list = new ArrayList<>();
        for (JsonElement value : GsonHelper.getAsJsonArray(object,key,new JsonArray()))
        { ResourceLocation id=ResourceLocation.tryParse(value.getAsString()); if(id==null) throw new JsonParseException("Invalid ID in " + key); list.add(id); }
        return List.copyOf(list);
    }
    private static List<String> texts(JsonObject object, String key)
    {
        List<String> list = new ArrayList<>();
        for(JsonElement value:GsonHelper.getAsJsonArray(object,key,new JsonArray())) list.add(value.getAsString());
        return List.copyOf(list);
    }
}
