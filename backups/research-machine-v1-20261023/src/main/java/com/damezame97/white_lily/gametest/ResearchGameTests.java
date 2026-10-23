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
        yes(h,before.getAsJsonArray("nodes").size()==1,"新玩家只收到一个可见节点");
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
        yes(h,research_service.canCraft(player,id("simple_bandage")),"其他配方不受本研究限制");
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
