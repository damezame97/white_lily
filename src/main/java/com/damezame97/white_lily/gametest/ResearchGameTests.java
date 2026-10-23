package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.research.*;
import com.google.gson.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** 实际库存、能力克隆及服务端裁剪快照的回归，不通过屏幕颜色假装权限。 */
@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ResearchGameTests
{
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("white_lily",name); }
    private static research_progress data(Player player) { return research_capability.get(player).orElseThrow(); }
    private static JsonObject find(JsonObject snapshot,String node)
    {
        for(JsonElement value:snapshot.getAsJsonArray("nodes"))
            if(value.getAsJsonObject().get("id").getAsString().equals(id(node).toString())) return value.getAsJsonObject();
        return null;
    }

    // ==================================================================
    // 研究门控：节点配方必须真的走 research_crafting_recipe / 带 research 的加工配方
    // ==================================================================

    /** 每个绑定到节点的配方都必须是被门控的配方类型，且不存在「绑了节点却没过门」的配方。 */
    @GameTest(template="empty")
    public static void nodeRecipesAreAlwaysGatedAndNoUnboundBypass(GameTestHelper h)
    {
        Player player=h.makeMockPlayer();
        var manager=h.getLevel().getRecipeManager();
        Map<ResourceLocation,ResourceLocation> owners=research_catalog.recipeOwners();
        yes(h,!owners.isEmpty(),"至少应该有一条节点配方，否则这条测试没意义");
        for(var entry:owners.entrySet())
        {
            var recipe=manager.byKey(entry.getKey()).orElse(null);
            yes(h,recipe!=null,"节点配方必须存在："+entry.getKey());
            boolean gated=recipe instanceof com.damezame97.white_lily.crafting.research_crafting_recipe
                    || recipe instanceof com.damezame97.white_lily.processing.processing_recipe processing
                    && processing.researchNode()!=null;
            // 关键：普通 crafting_shapeless 也是 ShapelessRecipe，但完全不看研究权限，必须被拦住。
            yes(h,gated,"节点配方必须是被门控的类型，实测 "+entry.getKey()+" 是 "+recipe.getClass().getName());
            yes(h,research_service.canCraft(player,entry.getKey())==false,"新玩家不得直接制作节点配方："+entry.getKey());
        }
        yes(h,research_service.invalidRecipes(player).isEmpty(),
                "当前配置不该有「绑定无效」的节点配方，实测 "+research_service.invalidRecipes(player));
        h.succeed();
    }

    /** 机器不得放行「绑了节点却没写 research」的配方；普通机器配方仍然能跑。 */
    @GameTest(template="empty")
    public static void machineRejectsNodeBoundRecipesWithoutResearchField(GameTestHelper h)
    {
        Map<ResourceLocation,ResourceLocation> owners=research_catalog.recipeOwners();
        yes(h,!owners.isEmpty(),"至少应该有一条节点配方");
        for(var entry:owners.entrySet())
        {
            // 就算机器已经记录过这条配方，缺 research 字段也不放行（防配置写错变成免费配方）。
            yes(h,!com.damezame97.white_lily.processing.processing_block_entity
                            .canProcessRecipe(entry.getKey(),null,true),
                    "绑节点但缺 research 字段必须拒绝："+entry.getKey());
            // research 字段写错（与节点不一致）同样拒绝。
            yes(h,!com.damezame97.white_lily.processing.processing_block_entity
                            .canProcessRecipe(entry.getKey(),id("wrong_node"),true),
                    "research 与节点不一致必须拒绝："+entry.getKey());
            // 完全正确的绑定：已记录才放行。
            yes(h,!com.damezame97.white_lily.processing.processing_block_entity
                            .canProcessRecipe(entry.getKey(),entry.getValue(),false),
                    "未记录的节点配方不能跑："+entry.getKey());
            yes(h,com.damezame97.white_lily.processing.processing_block_entity
                            .canProcessRecipe(entry.getKey(),entry.getValue(),true),
                    "已记录且绑定正确就应放行："+entry.getKey());
        }
        // 没绑节点、也没写 research：普通机器配方，放行。
        yes(h,com.damezame97.white_lily.processing.processing_block_entity
                .canProcessRecipe(id("processing/hemp_fiber"),null,false),"普通机器配方不受研究限制");
        // 写了 research 但没有任何节点登记：配置错，不放行。
        yes(h,!com.damezame97.white_lily.processing.processing_block_entity
                .canProcessRecipe(id("processing/hemp_fiber"),id("bitterleaf"),true),"孤儿 research 字段不放行");
        h.succeed();
    }

    /** 已拥有数量与提交扣料统一以主背包为准。 */
    @GameTest(template="empty")
    public static void ownedCountAndSubmitUseMainInventoryOnly(GameTestHelper h)
    {
        // 注意：这里不能用真合成格做实验。往 InventoryMenu 的合成格里放东西会走原版
        // CraftingMenu.slotChangedCraftingGrid，那里直接强转 ServerPlayer；而假 ServerPlayer 没有网络通道，
        // 又会在发结果槽更新时炸（Connection.channel() == null）。所以改用「与 count 同源」来锁住口径。
        Player player=h.makeMockPlayer();
        player.getInventory().setItem(7,new ItemStack(items.CALMING_FLOWER.get(),2));
        yes(h,research_service.ownedCount(player,items.CALMING_FLOWER.get())==2,"主背包里的数要算进去");
        yes(h,research_service.ownedCount(player,items.CALMING_FLOWER.get())
                ==research_service.count(player,items.CALMING_FLOWER.get()),"ownedCount 必须与主背包 count 同源");

        // 先把「发现」前提节点做完，否则提交会停在 LOCKED。
        research_service.discover(player);
        yes(h,data(player).has(id("calming_flower")),"主背包里有花即可完成发现节点");

        // 挪到光标上就不再算「已拥有」——以前这里会把光标算进去，于是「显示已拥有、提交却缺料」。
        player.containerMenu.setCarried(new ItemStack(items.CALMING_FLOWER.get(),2));
        player.getInventory().setItem(7,ItemStack.EMPTY);
        yes(h,research_service.ownedCount(player,items.CALMING_FLOWER.get())==0,"光标上的不算已拥有");
        yes(h,research_service.ownedCount(player,items.CALMING_FLOWER.get())
                ==research_service.count(player,items.CALMING_FLOWER.get()),"光标不影响主背包口径");

        long revision=research_service.revision(player);
        yes(h,research_service.submit(player,id("calming_flower_tea"),revision)
                ==research_service.SubmitResult.MISSING_MATERIALS,"光标上的材料不能交研究");
        yes(h,player.containerMenu.getCarried().getCount()==2,"失败时光标上的不被扣");

        // 放回主背包后两边都能用。
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().setItem(7,new ItemStack(items.CALMING_FLOWER.get(),1));
        player.getInventory().setItem(8,new ItemStack(items.BLANK_LABEL.get(),1));
        yes(h,research_service.ownedCount(player,items.CALMING_FLOWER.get())==1,"回到主背包后重新计数");
        yes(h,research_service.submit(player,id("calming_flower_tea"),research_service.revision(player))
                ==research_service.SubmitResult.COMPLETE,"主背包里的材料可以提交");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void quickHandbookReopenQueuesOnceWithoutBypassingRequestCooldown(GameTestHelper h)
    {
        research_progress state=new research_progress();
        yes(h,research_network.beginOpen(state,100),"第一次打开立即处理");
        research_network.close(state);
        yes(h,!research_network.beginOpen(state,100) && state.pendingOpen,"同刻重新打开进入队列，不丢弃也不绕过限流");
        for(int packet=0;packet<50;packet++) yes(h,!research_network.beginOpen(state,101),"洪泛请求合并，不能提前打开");
        yes(h,!research_network.takePendingOpen(state,101) && !research_network.takePendingOpen(state,102),"1/2刻尚不能补发");
        yes(h,research_network.takePendingOpen(state,103) && !state.pendingOpen,"3刻时补发一次");
        yes(h,!research_network.takePendingOpen(state,103),"同刻不重复补发");
        yes(h,!research_network.beginOpen(state,104) && state.pendingOpen,"下一限流窗口仍然有效");
        research_network.close(state);
        yes(h,!state.pendingOpen && !state.handbookOpen && !research_network.takePendingOpen(state,106),"关闭取消延迟打开，不能自行再弹出");
        yes(h,research_network.beginOpen(state,106),"窗口结束后仍可正常打开");
        state.pendingOpen=true;
        research_progress loaded=new research_progress(); loaded.load(state.save());
        yes(h,!loaded.pendingOpen && !loaded.handbookOpen,"待打开请求不写入永久存档");
        yes(h,research_network.takePendingOpen(state,10),"服务端时间回退后不会永久卡住队列");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void discoveryIsPersonalOwnedInventoryAndSnapshotsDoNotLeakHiddenTree(GameTestHelper h)
    {
        Player player=h.makeMockSurvivalPlayer();
        JsonObject before=research_service.buildSnapshot(player,false);
        yes(h,before.getAsJsonArray("nodes").size()==4,"新玩家只收到四个已配置发现根节点");
        JsonObject root=find(before,"calming_flower");
        yes(h,root!=null && root.get("state").getAsString().equals("discover"),"根节点灰色未发现");
        yes(h,!root.has("intro") && !root.has("archive") && !root.has("recipe"),"未发现不下发药效和制作档案");
        yes(h,find(before,"calming_flower_tea")==null && !before.toString().contains("calming_flower_tea"),"后续节点连ID都不下发");
        yes(h,!research_service.canCraft(player,id("calming_flower_tea")),"未研究花茶被锁定");
        player.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER_TEA.get()));
        yes(h,!research_service.discover(player) && !data(player).has(id("calming_flower_tea")),"赠送成品不代替发现/研究");
        player.getInventory().setItem(7,new ItemStack(items.CALMING_FLOWER.get(),4));
        yes(h,research_service.discover(player) && data(player).has(id("calming_flower")),"物品真正进入个人背包后完成");
        yes(h,player.getInventory().getItem(7).getCount()==4,"自动发现不消耗花");
        JsonObject after=research_service.buildSnapshot(player,false);
        yes(h,find(after,"calming_flower").get("state").getAsString().equals("complete"),"根节点完成");
        JsonObject tea=find(after,"calming_flower_tea");
        yes(h,tea!=null && tea.get("state").getAsString().equals("ready"),"只揭示下一层花茶");
        yes(h,tea.has("materials") && !tea.has("archive") && !tea.has("recipe"),"可研究显示提交材料但不下发完整配方");
        JsonObject patch=research_service.difference(before,after);
        yes(h,patch.get("mode").getAsString().equals("patch") && patch.getAsJsonArray("nodes").size()==2 && !patch.has("categories"),"进度增量只发变化根节点与新揭示下一层，不重复栏目");
        JsonObject unchanged=research_service.difference(after,after);
        yes(h,unchanged.getAsJsonArray("nodes").isEmpty() && unchanged.getAsJsonArray("addedUnlockedRecipes").isEmpty(),"无变化回复是空增量");
        yes(h,!research_service.canCraft(player,id("simple_bandage")),"新增绷带研究门槛同样保持锁定");
        yes(h,research_service.canCraft(player,id("coarse_cloth")),"基础材料不受研究限制");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void submitPrechecksAreAtomicAndDuplicateRequestsNeverConsumeAgain(GameTestHelper h)
    {
        Player player=h.makeMockPlayer(); // 创造模式同样验证和扣除研究材料。
        player.getInventory().setItem(5,new ItemStack(items.CALMING_FLOWER.get(),3));
        research_service.discover(player);
        long revision=research_service.revision(player);
        yes(h,research_service.submit(player,id("calming_flower_tea"),revision)==research_service.SubmitResult.MISSING_MATERIALS,"不足材料拒绝");
        yes(h,player.getInventory().getItem(5).getCount()==3,"预检失败不部分扣花");
        player.getInventory().setItem(30,new ItemStack(items.BLANK_LABEL.get(),2));
        JsonObject beforeComplete=research_service.buildSnapshot(player,false);
        yes(h,research_service.submit(player,id("calming_flower_tea"),revision-1)==research_service.SubmitResult.STALE,"旧界面版本拒绝");
        yes(h,research_service.submit(player,id("not_registered"),revision)==research_service.SubmitResult.INVALID,"伪造节点拒绝");
        yes(h,research_service.submit(player,id("calming_flower_tea"),revision)==research_service.SubmitResult.COMPLETE,"跨背包格材料一次提交");
        yes(h,player.getInventory().getItem(5).getCount()==2 && player.getInventory().getItem(30).getCount()==1,"只扣花1和标签1，创造也相同");
        yes(h,research_service.submit(player,id("calming_flower_tea"),revision)==research_service.SubmitResult.ALREADY_COMPLETE,"重复包不重扣");
        yes(h,player.getInventory().getItem(5).getCount()==2 && player.getInventory().getItem(30).getCount()==1,"重复请求库存不变");
        JsonObject complete=find(research_service.buildSnapshot(player,false),"calming_flower_tea");
        yes(h,complete.has("archive") && complete.has("recipe") && research_service.canCraft(player,id("calming_flower_tea")),"完成后立即解锁档案和制作权限");
        JsonObject recipe=complete.getAsJsonObject("recipe");
        yes(h,recipe.getAsJsonArray("ingredients").size()==3 && !recipe.has("duration") && !recipe.has("byproduct"),"普通合成不虚构加工时长和副产物");
        yes(h,recipe.toString().contains("minecraft:water"),"实际配方保留普通水瓶NBT");
        JsonObject delta=research_service.difference(beforeComplete,research_service.buildSnapshot(player,false));
        yes(h,delta.getAsJsonArray("nodes").size()==1 && delta.getAsJsonArray("addedUnlockedRecipes").size()==1,"花茶完成仅同步该节点与新增制作权限");
        Player other=h.makeMockSurvivalPlayer();
        yes(h,!research_service.canCraft(other,id("calming_flower_tea")) && !data(other).has(id("calming_flower")),"另一玩家不继承进度或权限");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void progressPersistsAcrossSaveDeathAndDimensionCloneWithoutItemNbt(GameTestHelper h)
    {
        Player original=h.makeMockSurvivalPlayer();
        data(original).complete(id("calming_flower")); data(original).complete(id("calming_flower_tea")); data(original).complete(id("removed_legacy_node"));
        CompoundTag saved=data(original).save();
        research_progress restored=new research_progress(); restored.load(saved);
        yes(h,restored.completed().equals(data(original).completed()),"保存加载保留未知旧ID，不错误映射");
        Player respawn=h.makeMockSurvivalPlayer();
        original.invalidateCaps();
        research_capability.clonePlayer(new PlayerEvent.Clone(respawn,original,true));
        yes(h,data(respawn).completed().equals(restored.completed()),"死亡克隆恢复旧能力后完整复制永久研究");
        Player dimension=h.makeMockSurvivalPlayer();
        respawn.invalidateCaps();
        research_capability.clonePlayer(new PlayerEvent.Clone(dimension,respawn,false));
        yes(h,data(dimension).has(id("calming_flower_tea")),"非死亡克隆也保留");
        yes(h,research_service.canCraft(dimension,id("calming_flower_tea")),"丢弃手册和全部材料不撤销制作权限");
        ItemStack first=new ItemStack(items.INVESTIGATION_HANDBOOK.get()), second=first.copy();
        yes(h,!first.hasTag() && !second.hasTag(),"实体手册不保存权威研究进度");
        CompoundTag invalid=new CompoundTag(); ListTag list=new ListTag(); list.add(StringTag.valueOf("invalid id!")); invalid.put("Completed",list);
        restored.load(invalid); yes(h,restored.completed().isEmpty(),"异常旧ID安全忽略");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void revealIsOneLayerWhileAllPrerequisitesAreStillRequired(GameTestHelper h)
    {
        JsonObject config=fixture(); JsonArray nodes=config.getAsJsonArray("nodes");
        JsonObject b=node("b",false,"submit"); b.getAsJsonArray("prerequisites").add(id("a").toString()); nodes.add(b);
        JsonObject c=node("c",false,"submit"); c.getAsJsonArray("prerequisites").add(id("b").toString()); c.getAsJsonArray("prerequisites").add(id("d").toString()); nodes.add(c);
        JsonObject d=node("d",true,"obtain"); nodes.add(d);
        var parsed=research_catalog.parse(Map.of(id("fixture"),config));
        research_progress state=new research_progress(); state.complete(id("a"));
        Set<ResourceLocation> visible=research_service.visible(state,parsed.nodes().values());
        yes(h,visible.contains(id("b")) && !visible.contains(id("c")),"完成A仅揭示B，不递归C");
        state.complete(id("b")); visible=research_service.visible(state,parsed.nodes().values());
        yes(h,visible.contains(id("c")) && !research_service.prerequisitesMet(state,parsed.nodes().get(id("c"))),"一个直接前置完成可揭示，但多前置未齐仍锁定");
        state.complete(id("d")); yes(h,research_service.prerequisitesMet(state,parsed.nodes().get(id("c"))),"全部前置完成才允许研究");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void invalidConfigurationIsRejectedWithStableIds(GameTestHelper h)
    {
        JsonObject duplicate=fixture(); duplicate.getAsJsonArray("nodes").add(node("a",true,"obtain")); rejects(h,duplicate,"重复ID");
        JsonObject missing=fixture(); missing.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("prerequisites").add(id("missing").toString()); rejects(h,missing,"缺失前置");
        JsonObject cycle=fixture(); cycle.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("prerequisites").add(id("a").toString()); rejects(h,cycle,"循环依赖");
        JsonObject invalidItem=fixture(); invalidItem.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("icon","white_lily:missing_item"); rejects(h,invalidItem,"无效物品");
        JsonObject invalidCount=fixture(); JsonObject bad=node("b",true,"submit"); bad.getAsJsonArray("materials").get(0).getAsJsonObject().addProperty("count",0); invalidCount.getAsJsonArray("nodes").add(bad); rejects(h,invalidCount,"数量0");
        JsonObject chapter=fixture(); chapter.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("chapter","white_lily:absent"); rejects(h,chapter,"缺失章节");
        var first=research_catalog.parse(Map.of(id("fixture"),fixture()));
        JsonObject moved=fixture(); moved.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("x",900);
        var second=research_catalog.parse(Map.of(id("fixture"),moved));
        yes(h,first.nodes().keySet().equals(second.nodes().keySet()),"坐标变化不改变稳定解锁ID");
        h.succeed();
    }
    private static void rejects(GameTestHelper h,JsonObject config,String label)
    { boolean rejected=false; try { research_catalog.parse(Map.of(id("fixture"),config)); } catch(JsonParseException expected) { rejected=true; } yes(h,rejected,label+"必须拒绝"); }
    private static JsonObject fixture()
    {
        JsonObject root=JsonParser.parseString("{\"categories\":[{\"id\":\"white_lily:test\",\"title\":\"test\",\"open\":true,\"chapters\":[{\"id\":\"white_lily:test_chapter\",\"title\":\"test\"}]}],\"nodes\":[]}").getAsJsonObject();
        root.getAsJsonArray("nodes").add(node("a",true,"obtain")); return root;
    }
    private static JsonObject node(String id,boolean root,String condition)
    {
        JsonObject n=new JsonObject(); n.addProperty("id",id(id).toString()); n.addProperty("category","white_lily:test"); n.addProperty("chapter","white_lily:test_chapter");
        n.addProperty("icon","white_lily:calming_flower"); n.addProperty("name","test"); n.addProperty("intro","test"); n.addProperty("hint","test"); n.addProperty("x",0); n.addProperty("y",0);
        n.add("prerequisites",new JsonArray()); n.addProperty("root_visible",root); n.addProperty("condition",condition); n.addProperty("target","white_lily:calming_flower");
        JsonArray materials=new JsonArray(); JsonObject m=new JsonObject(); m.addProperty("item","white_lily:calming_flower"); m.addProperty("count",1); materials.add(m); n.add("materials",materials);
        n.add("recipes",new JsonArray()); n.add("archive",new JsonArray()); return n;
    }
}
