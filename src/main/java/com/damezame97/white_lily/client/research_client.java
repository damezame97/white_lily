package com.damezame97.white_lily.client;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.research.research_network;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;

/** 客户端仅保存服务端已允许显示的快照；这里不读取完整研究配置。 */
@Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT)
public final class research_client
{
    public static final String KEY = "screen.white_lily.research.";
    private static Snapshot snapshot = new Snapshot(0, List.of(), List.of(), Set.of(),false,false,"",0);
    private static JsonObject displayData = new JsonObject();
    @Nullable private static Screen pendingParent;
    private static boolean awaitingOpen;

    /** 手持使用的客户端分支仅记录返回位置，打开确认由服务端返回。 */
    public static void prepareItemOpen()
    {
        pendingParent = null;
        awaitingOpen = true;
    }

    public static void openFromItem()
    {
        prepareItemOpen();
        research_network.requestOpen();
    }

    /** True while an item/button/shortcut open request is waiting for the server snapshot. */
    public static boolean isOpening()
    {
        return awaitingOpen;
    }

    private static void openFromInventory(Screen parent)
    {
        pendingParent = parent;
        awaitingOpen = true;
        research_network.requestOpen();
    }

    public static boolean canCraft(ResourceLocation recipe)
    {
        return snapshot.unlockedRecipes().contains(recipe.toString());
    }

    public static void accept(JsonObject payload)
    {
        displayData = string(payload, "mode", "full").equals("patch") ? merge(displayData, payload) : payload.deepCopy();
        snapshot = decode(displayData);
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof InvestigationScreen screen)
        {
            screen.update(snapshot);
            return;
        }
        if (bool(payload, "open", false) && awaitingOpen)
        {
            if (client.screen != pendingParent)
            {
                pendingParent = null;
                awaitingOpen = false;
                research_network.requestClose();
                return;
            }
            Screen parent = pendingParent;
            pendingParent = null;
            awaitingOpen = false;
            client.setScreen(new InvestigationScreen(parent, snapshot));
        }
    }

    @SubscribeEvent
    public static void inventory(ScreenEvent.Init.Post event)
    {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
        if (event.getListenersList().stream().anyMatch(HandbookButton.class::isInstance)) return;
        event.addListener(new HandbookButton(screen));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event)
    {
        snapshot = new Snapshot(0, List.of(), List.of(), Set.of(),false,false,"",0);
        displayData = new JsonObject();
        pendingParent = null;
        awaitingOpen = false;
        InvestigationScreen.clearViews();
    }

    private static final class HandbookButton extends AbstractButton
    {
        private final Screen parent;
        private HandbookButton(Screen parent)
        {
            super(6, 6, 20, 20, Component.translatable(KEY + "open"));
            this.parent = parent;
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override public void onPress() { openFromInventory(parent); }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            InvestigationScreen.panel(graphics, getX(), getY(), width, height, isHoveredOrFocused() ? 2 : 1);
            graphics.renderItem(new ItemStack(items.INVESTIGATION_HANDBOOK.get()), getX() + 2, getY() + 2);
        }

        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output)
        {
            defaultButtonNarrationText(output);
        }
    }

    @Mod.EventBusSubscriber(modid = "white_lily", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload
    {
        @SubscribeEvent
        public static void sprites(net.minecraftforge.client.event.TextureStitchEvent.Post event)
        {
            Minecraft.getInstance().execute(InvestigationScreen::clearGrayIcons);
        }
        private Reload() { }
    }

    public record Chapter(String id, String title) { }
    public record Category(String id, String title, boolean open, List<Chapter> chapters) { }
    public record Requirement(String id, String name, boolean complete, String jump) { }
    public record Material(ItemStack stack, int count, int owned) { }
    public record Recipe(String type, String device, List<Material> ingredients, ItemStack result,
                         int ticks, ItemStack returned, ItemStack byproduct, ItemStack tool, int toolDamage) { }
    public record Node(String id, String category, String chapter, ItemStack icon, String name, String state,
                       double x, double y, String hint, String intro, List<Requirement> prerequisites,
                       List<Material> materials, List<String> archive, @Nullable Recipe recipe, List<Recipe> recipes,
                       List<String> completeImpact,List<String> resetImpact)
    {
        public boolean complete() { return state.equals("complete"); }
        public boolean ready() { return state.equals("ready"); }
    }
    public record Snapshot(long revision, List<Category> categories, List<Node> nodes, Set<String> unlockedRecipes,
                           boolean canEdit,boolean editing,String notice,int noticeCount) { }

    private static JsonObject merge(JsonObject previous, JsonObject patch)
    {
        JsonObject merged = previous.deepCopy();
        merged.addProperty("revision", integer(patch, "revision", integer(previous, "revision", 0)));
        if (patch.has("categories")) merged.add("categories", patch.get("categories").deepCopy());
        for(String key:List.of("canEdit","editing","notice","noticeCount"))
            if(patch.has(key)) merged.add(key,patch.get(key).deepCopy());
        var visible = new LinkedHashMap<String, JsonElement>();
        for (JsonElement n : array(previous, "nodes")) visible.put(string(n.getAsJsonObject(), "id", ""), n);
        for (JsonElement id : array(patch, "removedNodes")) visible.remove(id.getAsString());
        for (JsonElement n : array(patch, "nodes")) visible.put(string(n.getAsJsonObject(), "id", ""), n.deepCopy());
        JsonArray nodes = new JsonArray();
        visible.values().forEach(nodes::add);
        merged.add("nodes", nodes);
        Set<String> unlocked = new HashSet<>();
        for (JsonElement id : array(previous, "unlockedRecipes")) unlocked.add(id.getAsString());
        for (JsonElement id : array(patch, "removedUnlockedRecipes")) unlocked.remove(id.getAsString());
        for (JsonElement id : array(patch, "addedUnlockedRecipes")) unlocked.add(id.getAsString());
        JsonArray recipes = new JsonArray();
        unlocked.forEach(recipes::add);
        merged.add("unlockedRecipes", recipes);
        return merged;
    }

    private static Snapshot decode(JsonObject data)
    {
        List<Category> categories = new ArrayList<>();
        for (JsonElement entry : array(data, "categories"))
        {
            JsonObject value = entry.getAsJsonObject();
            List<Chapter> chapters = new ArrayList<>();
            for (JsonElement child : array(value, "chapters"))
            {
                JsonObject c = child.getAsJsonObject();
                chapters.add(new Chapter(string(c, "id", ""), string(c, "title", "")));
            }
            categories.add(new Category(string(value, "id", ""), string(value, "title", ""),
                    bool(value, "open", false), List.copyOf(chapters)));
        }
        List<Node> nodes = new ArrayList<>();
        for (JsonElement entry : array(data, "nodes"))
        {
            JsonObject n = entry.getAsJsonObject();
            List<Requirement> prerequisites = new ArrayList<>();
            for (JsonElement child : array(n, "prerequisites"))
            {
                JsonObject p = child.getAsJsonObject();
                prerequisites.add(new Requirement(string(p, "id", ""), string(p, "name", ""), bool(p, "complete", false),string(p,"jump","")));
            }
            List<String> archive = new ArrayList<>();
            for (JsonElement line : array(n, "archive")) archive.add(line.getAsString());
            List<Recipe> recipes=new ArrayList<>();
            for(JsonElement entryRecipe:array(n,"recipes"))recipes.add(decodeRecipe(entryRecipe.getAsJsonObject()));
            if(recipes.isEmpty() && n.has("recipe"))recipes.add(decodeRecipe(n.getAsJsonObject("recipe")));
            Recipe recipe=recipes.isEmpty()?null:recipes.get(0);
            nodes.add(new Node(string(n, "id", ""), string(n, "category", ""), string(n, "chapter", ""),
                    stack(n.get("icon")), string(n, "name", ""), string(n, "state", "locked"),
                    number(n, "x", 0), number(n, "y", 0), string(n, "hint", ""), string(n, "intro", ""),
                    List.copyOf(prerequisites), materials(n, "materials"), List.copyOf(archive), recipe, List.copyOf(recipes),
                    strings(n,"completeImpact"),strings(n,"resetImpact")));
        }
        Set<String> unlocked = new HashSet<>();
        for (JsonElement value : array(data, "unlockedRecipes")) unlocked.add(value.getAsString());
        return new Snapshot(integer(data, "revision", 0), List.copyOf(categories), List.copyOf(nodes), Set.copyOf(unlocked),
                bool(data,"canEdit",false),bool(data,"editing",false),string(data,"notice",""),(int)number(data,"noticeCount",0));
    }
    private static Recipe decodeRecipe(JsonObject r)
    {
        return new Recipe(string(r,"type",""),string(r,"device","crafting_table"),materials(r,"ingredients"),stack(r.getAsJsonObject("result")),
            (int)number(r,"ticks",0),stack(r.get("returned")),stack(r.get("byproduct")),stack(r.get("tool")),(int)number(r,"toolDamage",0));
    }
    private static List<String> strings(JsonObject data,String key)
    { List<String> result=new ArrayList<>(); for(JsonElement value:array(data,key)) result.add(value.getAsString()); return List.copyOf(result); }

    private static List<Material> materials(JsonObject data, String key)
    {
        List<Material> result = new ArrayList<>();
        for (JsonElement entry : array(data, key))
        {
            JsonObject m = entry.getAsJsonObject();
            result.add(new Material(stack(m), (int) number(m, "count", 1), (int) number(m, "owned", 0)));
        }
        return List.copyOf(result);
    }

    private static ItemStack stack(@Nullable JsonElement value)
    {
        if (value == null || value.isJsonNull()) return ItemStack.EMPTY;
        JsonObject data = value.isJsonPrimitive() ? new JsonObject() : value.getAsJsonObject();
        String item = value.isJsonPrimitive() ? value.getAsString() : string(data, "item", "minecraft:air");
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id), Math.max(1, (int) number(data, "count", 1)));
        if (data.has("nbt"))
        {
            try { stack.setTag(TagParser.parseTag(data.get("nbt").isJsonPrimitive() ? data.get("nbt").getAsString() : data.get("nbt").toString())); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { /* 显示信息失效不影响服务器权限。 */ }
        }
        return stack;
    }

    private static JsonArray array(JsonObject data, String key)
    {
        return data.has(key) && data.get(key).isJsonArray() ? data.getAsJsonArray(key) : new JsonArray();
    }
    private static String string(JsonObject data, String key, String fallback)
    {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : fallback;
    }
    private static boolean bool(JsonObject data, String key, boolean fallback)
    {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsBoolean() : fallback;
    }
    private static double number(JsonObject data, String key, double fallback)
    {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsDouble() : fallback;
    }
    private static long integer(JsonObject data, String key, long fallback)
    {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsLong() : fallback;
    }

    private research_client() { }
}
