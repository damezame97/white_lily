package com.damezame97.white_lily.research;

import com.google.gson.*;
import com.damezame97.white_lily.processing.processing_recipe;
import com.damezame97.white_lily.crafting.research_crafting_recipe;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** 服务端权威入口：进度与原料扣除在同一服务器线程完成；客户端不提供任意解锁入口。 */
public final class research_service
{
    public static final ResourceLocation TEA = ResourceLocation.fromNamespaceAndPath("white_lily", "calming_flower_tea");
    public enum SubmitResult { COMPLETE, STALE, INVALID, LOCKED, MISSING_MATERIALS, ALREADY_COMPLETE }
    private static long validatedGeneration = Long.MIN_VALUE;
    private static Object validatedManager;
    private static Set<ResourceLocation> invalidRecipes = Set.of();

    public static long revision(Player player)
    { return research_capability.get(player).map(data -> (research_catalog.generation() << 32) ^ data.revision()).orElse(-1L); }
    public static boolean canCraft(Player player, ResourceLocation recipe)
    {
        if (player == null || recipe == null) return false;
        validateRecipes(player);
        ResourceLocation owner = research_catalog.recipeOwners().get(recipe);
        // 本轮唯一路线在配置损坏/缺失时也保持锁定，不能因映射消失变为免费配方。
        if (recipe.equals(TEA) && owner == null) return false;
        if (owner == null)
        {
            Recipe<?> actual=player.level().getRecipeManager().byKey(recipe).orElse(null);
            return !(actual instanceof research_crafting_recipe)
                    && !(actual instanceof processing_recipe processing && processing.researchNode()!=null);
        }
        return !invalidRecipes.contains(recipe) && research_capability.get(player).map(data -> data.has(owner)).orElse(false);
    }
    private static void validateRecipes(Player player)
    {
        if (player.level().isClientSide) return;
        Object manager = player.level().getRecipeManager();
        if (validatedManager == manager && validatedGeneration == research_catalog.generation()) return;
        Set<ResourceLocation> missing = new HashSet<>();
        for (ResourceLocation recipe : research_catalog.recipeOwners().keySet())
        {
            Recipe<?> actual=player.level().getRecipeManager().byKey(recipe).orElse(null);
            if (actual==null)
            {
                missing.add(recipe);
                LogUtils.getLogger().error("White Lily research: missing recipe {} (node {})", recipe, research_catalog.recipeOwners().get(recipe));
            }
            else if(!(actual instanceof research_crafting_recipe) && !(actual instanceof processing_recipe processing && processing.researchNode()!=null && processing.researchNode().equals(research_catalog.recipeOwners().get(recipe))))
            {
                missing.add(recipe);
                LogUtils.getLogger().error("White Lily research: invalid recipe binding {} type {} (node {}); expected research crafting recipe or processing with matching research ID",recipe,actual.getClass().getName(),research_catalog.recipeOwners().get(recipe));
            }
        }
        invalidRecipes = Set.copyOf(missing);
        validatedGeneration = research_catalog.generation(); validatedManager = manager;
    }
    /** 供测试与调试：当前配置下被判为「绑定无效」的节点配方（不含缺失的配方）。 */
    public static Set<ResourceLocation> invalidRecipes(Player player)
    {
        validateRecipes(player);
        return invalidRecipes;
    }

    public static Set<ResourceLocation> visible(research_progress data)
    {
        Set<ResourceLocation> open=new HashSet<>();
        for(research_catalog.Category category:research_catalog.categories()) if(category.open()) open.add(category.id());
        return visible(data,research_catalog.nodes().values().stream().filter(node->open.contains(node.category())).toList());
    }
    /** 同样的单层揭示算法也供配置验证测试使用，不改全局资源表。 */
    public static Set<ResourceLocation> visible(research_progress data, Collection<research_catalog.Node> nodes)
    {
        Set<ResourceLocation> visible = new LinkedHashSet<>();
        for (research_catalog.Node node : nodes)
            if (data.has(node.id()) || node.rootVisible() || node.revealAfter().stream().anyMatch(data::has)) visible.add(node.id());
        return visible;
    }
    public static boolean prerequisitesMet(research_progress data, research_catalog.Node node)
    { return node.prerequisites().stream().allMatch(data::has); }
    /** 每20刻调用；只扫描自身库存的物品索引，附近掉落物与其他容器从不参与。 */
    public static boolean discover(Player player)
    { return discover(player,false)>0; }

    /** 手动检查只用开始时可见集合；重置抑制只由明确检查或新的实际获得解除。 */
    public static int discover(Player player, boolean explicit)
    {
        if (player.level().isClientSide || !player.isAlive()) return 0;
        research_progress data = research_capability.get(player).orElse(null);
        if (data == null || research_catalog.discoveries().isEmpty()) return 0;
        Set<ResourceLocation> visibleBefore = visible(data);
        Set<Item> owned = new HashSet<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && research_catalog.discoveries().containsKey(stack.getItem())) owned.add(stack.getItem());
        }
        int completed=0;
        for(var entry:research_catalog.discoveries().entrySet())
            for(research_catalog.Node node:entry.getValue())
            {
                int total=ownedCount(player,entry.getKey());
                boolean fresh=!data.suppressed(node.id()) || total>data.observed(node.id());
                if(owned.contains(entry.getKey()) && (explicit||fresh) && visibleBefore.contains(node.id())
                        && prerequisitesMet(data,node) && data.complete(node.id())) completed++;
                data.observe(node.id(),total);
            }
        if(completed>0) changed(player);
        return completed;
    }

    /**
     * 只统计<b>主背包</b>里的数量。
     *
     * <p>之前这里把「自己的合成格 / 当前打开的合成菜格 / 光标」也算进去，但 {@link #submit}
     * 只从主背包扣料，于是会出现「界面显示已拥有、提交却缺料」的口径不一致。
     * 现在统一以主背包为准（与 {@link #count} 同源），移动物品到合成格不再算新增获得。</p>
     */
    public static int ownedCount(Player player,Item item)
    {
        return count(player,item);
    }
    public static void acquired(Player player,Item item)
    {
        if(player.level().isClientSide || !player.isAlive() || count(player,item)==0) return;
        research_progress data=research_capability.get(player).orElse(null);
        if(data==null) return;
        Set<ResourceLocation> visibleBefore=visible(data);
        boolean changed=false;
        for(var node:research_catalog.discoveries().getOrDefault(item,List.of()))
            if(visibleBefore.contains(node.id()) && prerequisitesMet(data,node)) changed|=data.complete(node.id());
        if(changed) changed(player);
    }
    public static int checkInventory(Player player)
    {
        research_progress data=research_capability.get(player).orElse(null);
        if(data==null || player.level().isClientSide) return -1;
        long tick=player.level().getGameTime();
        if(data.lastCheckTick!=Long.MIN_VALUE && tick>=data.lastCheckTick && tick-data.lastCheckTick<20) return -1;
        data.lastCheckTick=tick;
        int count=discover(player,true);
        data.notice=count==0?"checked_none":"checked"; data.noticeCount=count;
        if(player instanceof ServerPlayer server) acknowledge(server);
        return count;
    }
    public static boolean canEdit(Player player) { return !player.level().isClientSide && player.hasPermissions(2); }
    public static boolean setEditing(Player player,boolean enable,long expected)
    {
        research_progress data=research_capability.get(player).orElse(null);
        if(data==null || expected!=revision(player) || (enable&&!canEdit(player))) return false;
        if(data.editing!=enable) data.touch();
        data.editing=enable; data.notice=enable?"editing_on":"editing_off";
        if(player instanceof ServerPlayer server) acknowledge(server);
        return true;
    }
    public enum EditResult { APPLIED, DENIED, STALE, INVALID, UNCHANGED }
    public static Set<ResourceLocation> editPlan(research_progress data,ResourceLocation id,boolean complete)
    {
        Set<ResourceLocation> affected=new LinkedHashSet<>();
        if(!research_catalog.nodes().containsKey(id)) return affected;
        affected.add(id);
        boolean grew;
        do
        {
            grew=false;
            if(complete)
            {
                for(ResourceLocation next:List.copyOf(affected))
                    for(ResourceLocation parent:research_catalog.nodes().get(next).prerequisites()) grew|=affected.add(parent);
            }
            else for(var node:research_catalog.nodes().values())
                if(data.has(node.id()) && node.prerequisites().stream().anyMatch(affected::contains)) grew|=affected.add(node.id());
        } while(grew);
        affected.removeIf(next->complete?data.has(next):!data.has(next));
        return affected;
    }
    public static EditResult edit(Player player,ResourceLocation id,boolean complete,long expected)
    {
        research_progress data=research_capability.get(player).orElse(null);
        if(data==null || !canEdit(player) || !data.editing) return EditResult.DENIED;
        if(expected!=revision(player)) return EditResult.STALE;
        if(id==null || !research_catalog.nodes().containsKey(id) || !player.isAlive()) return EditResult.INVALID;
        if(data.has(id)==complete) return EditResult.UNCHANGED;
        Set<ResourceLocation> affected=editPlan(data,id,complete);
        validateRecipes(player);
        if(complete && affected.stream().map(research_catalog.nodes()::get)
                .anyMatch(node->node.recipes().stream().anyMatch(invalidRecipes::contains))) return EditResult.INVALID;
        // 全部受影响ID由服务端计算；没有材料/奖励回调，也不模拟获得事件。
        for(ResourceLocation next:affected)
        {
            var node=research_catalog.nodes().get(next);
            if(complete) data.complete(next);
            else data.reset(next,node.condition().equals("obtain")?ownedCount(player,node.target()):-1);
        }
        data.notice=complete?"edit_completed":"edit_reset"; data.noticeCount=affected.size();
        changed(player);
        return EditResult.APPLIED;
    }
    public static SubmitResult submit(Player player, ResourceLocation id, long expectedRevision)
    {
        if (player == null || player.level().isClientSide || !player.isAlive() || id == null) return SubmitResult.INVALID;
        research_progress data = research_capability.get(player).orElse(null);
        research_catalog.Node node = research_catalog.nodes().get(id);
        if (data == null || node == null || !node.condition().equals("submit")) return SubmitResult.INVALID;
        validateRecipes(player);
        if (node.recipes().stream().anyMatch(invalidRecipes::contains)) return SubmitResult.INVALID;
        if (data.has(id)) return SubmitResult.ALREADY_COMPLETE;
        if (expectedRevision != revision(player)) return SubmitResult.STALE;
        if (!visible(data).contains(id) || !prerequisitesMet(data,node)) return SubmitResult.LOCKED;
        Map<Item, Integer> needed = required(node);
        int[] consume = new int[player.getInventory().getContainerSize()];
        for (int slot = 0; slot < consume.length; slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);
            int remaining = needed.getOrDefault(stack.getItem(),0);
            int take = Math.min(remaining, stack.getCount());
            consume[slot] = take;
            if (take > 0) needed.put(stack.getItem(), remaining - take);
        }
        if (needed.values().stream().anyMatch(count -> count > 0)) return SubmitResult.MISSING_MATERIALS;
        // 预检完成后才扣料，同一线程没有中间回调；重复包首先命中已完成分支。
        for (int slot = 0; slot < consume.length; slot++) if (consume[slot] > 0) player.getInventory().getItem(slot).shrink(consume[slot]);
        player.getInventory().setChanged();
        data.complete(id);
        changed(player);
        return SubmitResult.COMPLETE;
    }
    private static Map<Item,Integer> required(research_catalog.Node node)
    {
        Map<Item,Integer> needed = new LinkedHashMap<>();
        for (research_catalog.Material material:node.materials()) needed.merge(material.item(),material.count(),Integer::sum);
        return needed;
    }
    public static int count(Player player, Item item)
    {
        int count=0;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)
        { ItemStack stack=player.getInventory().getItem(slot); if(stack.is(item)) count+=stack.getCount(); }
        return count;
    }
    private static void changed(Player player)
    {
        // 先同步新权限，随后才发原版配方书通知与结果槽刷新。
        if (player instanceof ServerPlayer server) reconcile(server);
    }
    /** 登录/重生/资源重载也修复原版配方书通知，不要求发生新的研究变化。 */
    public static void reconcile(ServerPlayer server)
    {
        if (server.connection != null)
        {
            refresh(server,false);
            List<Recipe<?>> locked=new ArrayList<>();
            for(Recipe<?> recipe:server.level().getRecipeManager().getRecipes())
                if(recipe instanceof research_crafting_recipe && !canCraft(server,recipe.getId())) locked.add(recipe);
            if(!locked.isEmpty()) server.resetRecipes(locked);
            List<Recipe<?>> unlocked=new ArrayList<>();
            for (ResourceLocation id:research_catalog.recipeOwners().keySet())
                if(canCraft(server,id)) server.level().getRecipeManager().byKey(id).filter(recipe->recipe instanceof research_crafting_recipe).ifPresent(unlocked::add);
            if(!unlocked.isEmpty()) server.awardRecipes(unlocked);
            research_crafting_gate.refresh(server);
            server.containerMenu.broadcastChanges();
        }
    }
    public static void refresh(ServerPlayer player, boolean open)
    { sync(player,open,false); }
    /** 拒绝/重复提交也返回空增量确认，不重复发送整棵可见树。 */
    public static void acknowledge(ServerPlayer player) { sync(player,false,true); }
    private static void sync(ServerPlayer player,boolean open,boolean reply)
    {
        if(player.connection==null) return;
        research_progress data=research_capability.get(player).orElse(null);
        if(data==null) return;
        if(open) { data.handbookOpen=true; data.editing=false; data.notice=""; discover(player); }
        JsonObject snapshot=buildSnapshot(player,open);
        snapshot.addProperty("open",false);
        String serialized=snapshot.toString();
        if(open || reply || !serialized.equals(data.lastSnapshot))
        {
            JsonObject packet=snapshot.deepCopy();
            if(!open && !data.lastSnapshot.isEmpty())
            {
                JsonObject previous=JsonParser.parseString(data.lastSnapshot).getAsJsonObject();
                if((previous.get("revision").getAsLong() >>> 32)==(snapshot.get("revision").getAsLong() >>> 32)) packet=difference(previous,snapshot);
            }
            packet.addProperty("open",open);
            research_network.send(player,packet);
            data.lastSnapshot=serialized;
        }
    }
    /** 只比较服务器已经裁剪的可见数据，绝不把隐藏配置加入增量缓存。 */
    public static JsonObject difference(JsonObject before,JsonObject after)
    {
        JsonObject patch=new JsonObject(); patch.addProperty("mode","patch"); patch.addProperty("open",false); patch.add("revision",after.get("revision"));
        Map<String,JsonElement> oldNodes=new LinkedHashMap<>();
        for(JsonElement node:before.getAsJsonArray("nodes")) oldNodes.put(node.getAsJsonObject().get("id").getAsString(),node);
        JsonArray changed=new JsonArray(),removed=new JsonArray();
        for(JsonElement node:after.getAsJsonArray("nodes"))
        {
            String id=node.getAsJsonObject().get("id").getAsString();
            if(!node.equals(oldNodes.remove(id))) changed.add(node.deepCopy());
        }
        oldNodes.keySet().forEach(removed::add); patch.add("nodes",changed); patch.add("removedNodes",removed);
        Set<String> oldRecipes=new LinkedHashSet<>(),newRecipes=new LinkedHashSet<>();
        before.getAsJsonArray("unlockedRecipes").forEach(id->oldRecipes.add(id.getAsString()));
        after.getAsJsonArray("unlockedRecipes").forEach(id->newRecipes.add(id.getAsString()));
        JsonArray added=new JsonArray(),deleted=new JsonArray();
        newRecipes.stream().filter(id->!oldRecipes.contains(id)).forEach(added::add);
        oldRecipes.stream().filter(id->!newRecipes.contains(id)).forEach(deleted::add);
        patch.add("addedUnlockedRecipes",added); patch.add("removedUnlockedRecipes",deleted);
        if(!before.get("categories").equals(after.get("categories"))) patch.add("categories",after.get("categories").deepCopy());
        for(String key:List.of("canEdit","editing","notice","noticeCount"))
            if(after.has(key) && !after.get(key).equals(before.get(key))) patch.add(key,after.get(key).deepCopy());
        return patch;
    }
    /** 仅包含已经揭示的节点；未完成节点从不发送archive或recipe字段。 */
    public static JsonObject buildSnapshot(Player player, boolean open)
    {
        validateRecipes(player);
        JsonObject result=new JsonObject(); result.addProperty("mode","full"); result.addProperty("open",open); result.addProperty("revision",revision(player));
        JsonArray categories=new JsonArray();
        for(research_catalog.Category category:research_catalog.categories())
        {
            JsonObject c=new JsonObject(); c.addProperty("id",category.id().toString()); c.addProperty("title",category.title()); c.addProperty("open",category.open());
            JsonArray chapters=new JsonArray();
            for(research_catalog.Chapter chapter:category.chapters())
            { JsonObject ch=new JsonObject(); ch.addProperty("id",chapter.id().toString()); ch.addProperty("title",chapter.title()); chapters.add(ch); }
            c.add("chapters",chapters); categories.add(c);
        }
        result.add("categories",categories);
        JsonArray nodes=new JsonArray(), unlocked=new JsonArray();
        research_progress data=research_capability.get(player).orElse(null);
        boolean authorized=canEdit(player);
        if(data!=null && !authorized) data.editing=false;
        boolean editing=data!=null && data.editing && authorized;
        result.addProperty("canEdit",authorized); result.addProperty("editing",editing);
        result.addProperty("notice",data==null?"":data.notice); result.addProperty("noticeCount",data==null?0:data.noticeCount);
        if(data!=null)
        {
            // 关闭栏目不撤销已经永久获得的制作权限；但该栏目档案仍不下发。
            for(var entry:research_catalog.recipeOwners().entrySet())
                if(data.has(entry.getValue()) && !invalidRecipes.contains(entry.getKey())) unlocked.add(entry.getKey().toString());
            Set<ResourceLocation> visible=visible(data);
            if(editing) visible=new LinkedHashSet<>(research_catalog.nodes().keySet());
            for(research_catalog.Node node:research_catalog.nodes().values())
            {
                if(!visible.contains(node.id())) continue;
                boolean complete=data.has(node.id()), met=prerequisitesMet(data,node);
                String state=complete?"complete":!met?"locked":node.condition().equals("obtain")?"discover":"ready";
                JsonObject n=new JsonObject(); n.addProperty("id",node.id().toString()); n.addProperty("category",node.category().toString()); n.addProperty("chapter",node.chapter().toString());
                n.addProperty("icon",ForgeRegistries.ITEMS.getKey(node.icon()).toString()); n.addProperty("name",node.name()); n.addProperty("state",state);
                n.addProperty("x",node.x()); n.addProperty("y",node.y()); n.addProperty("hint",node.hint());
                if(editing)
                {
                    JsonArray finish=new JsonArray(),reset=new JsonArray();
                    for(ResourceLocation next:editPlan(data,node.id(),true)) finish.add(research_catalog.nodes().get(next).name());
                    for(ResourceLocation next:editPlan(data,node.id(),false)) reset.add(research_catalog.nodes().get(next).name());
                    n.add("completeImpact",finish); n.add("resetImpact",reset);
                }
                JsonArray parents=new JsonArray();
                for(ResourceLocation parent:node.prerequisites())
                {
                    JsonObject p=new JsonObject(); p.addProperty("complete",data.has(parent));
                    if(visible.contains(parent)) { p.addProperty("id",parent.toString()); p.addProperty("name",research_catalog.nodes().get(parent).name()); }
                    else p.addProperty("name","research.white_lily.unknown_prerequisite");
                    // A hidden prerequisite can only link to an already visible clue, never disclose its archive.
                    ResourceLocation jump=visible.contains(parent)?parent:research_catalog.nodes().get(parent).prerequisites().stream().filter(visible::contains).findFirst().orElse(null);
                    if(jump!=null)p.addProperty("jump",jump.toString());
                    parents.add(p);
                }
                n.add("prerequisites",parents);
                if(state.equals("ready")||complete) n.addProperty("intro",node.intro());
                if(!complete && !node.condition().equals("obtain"))
                {
                    JsonArray materials=new JsonArray();
                    for(var material:required(node).entrySet())
                    { JsonObject m=new JsonObject(); m.addProperty("item",ForgeRegistries.ITEMS.getKey(material.getKey()).toString()); m.addProperty("count",material.getValue()); m.addProperty("owned",count(player,material.getKey())); materials.add(m); }
                    n.add("materials",materials);
                }
                if(complete)
                {
                    JsonArray archive=new JsonArray(); node.archive().forEach(archive::add); n.add("archive",archive);
                    JsonArray recipes=new JsonArray();
                    for(ResourceLocation id:node.recipes())
                    {
                        if(invalidRecipes.contains(id)) continue;
                        player.level().getRecipeManager().byKey(id).ifPresent(recipe->{ JsonObject recipeJson=recipeDisplay(player,recipe); recipes.add(recipeJson); if(!n.has("recipe")) n.add("recipe",recipeJson); });
                    }
                    n.add("recipes",recipes);
                }
                nodes.add(n);
            }
        }
        result.add("nodes",nodes); result.add("unlockedRecipes",unlocked); return result;
    }
    private static JsonObject recipeDisplay(Player player, Recipe<?> recipe)
    {
        if(recipe instanceof processing_recipe processing)
        {
            JsonObject result=new JsonObject();result.addProperty("id",recipe.getId().toString());result.addProperty("type","processing");result.addProperty("device","simple_processing_table");
            result.addProperty("ticks",processing.duration());result.addProperty("toolDamage",processing.toolDamage());
            result.add("tool",stack(processing.tool().getItems()[0]));
            JsonArray ingredients=new JsonArray();
            for(var material:processing.materials())
            {JsonObject entry=stack(material.ingredient().getItems()[0]);entry.addProperty("count",material.count());ingredients.add(entry);}
            if(processing.container()!=null){var material=processing.container();JsonObject entry=stack(material.ingredient().getItems()[0]);entry.addProperty("count",material.count());ingredients.add(entry);}
            result.add("ingredients",ingredients);result.add("result",stack(processing.result()));
            if(!processing.returned().isEmpty())result.add("returned",stack(processing.returned()));
            if(!processing.byproduct().isEmpty())result.add("byproduct",stack(processing.byproduct()));return result;
        }
        if(!(recipe instanceof ShapelessRecipe)) throw new IllegalArgumentException("Unsupported research recipe display " + recipe.getId());
        JsonObject result=new JsonObject(); result.addProperty("id",recipe.getId().toString()); result.addProperty("type","crafting_shapeless"); result.addProperty("device","crafting_table");
        JsonArray ingredients=new JsonArray();
        ItemStack remainder=ItemStack.EMPTY;
        for(var ingredient:recipe.getIngredients())
        {
            ItemStack[] examples=ingredient.getItems(); if(examples.length==0) continue;
            JsonObject entry=stack(examples[0]); entry.addProperty("count",1); ingredients.add(entry);
            // Ordinary crafting container returns come from the actual ingredient, not a guessed device.
            if(examples[0].hasCraftingRemainingItem())
            {
                ItemStack returned=examples[0].getCraftingRemainingItem();
                if(remainder.isEmpty())remainder=returned.copy();
                else if(ItemStack.isSameItemSameTags(remainder,returned))remainder.grow(returned.getCount());
            }
        }
        if(!remainder.isEmpty())result.add("returned",stack(remainder));
        result.add("ingredients",ingredients); result.add("result",stack(recipe.getResultItem(player.level().registryAccess()))); return result;
    }
    private static JsonObject stack(ItemStack stack)
    {
        JsonObject entry=new JsonObject(); entry.addProperty("item",ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()); entry.addProperty("count",stack.getCount());
        if(stack.hasTag()) entry.addProperty("nbt",stack.getTag().toString()); return entry;
    }
    private research_service() { }
}
