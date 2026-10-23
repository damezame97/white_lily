package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.research.*;
import com.damezame97.white_lily.client.research_tree_view;
import com.damezame97.white_lily.client.research_preferences;
import com.damezame97.white_lily.item.items;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.nio.file.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class ResearchEditingGameTests
{
    private static final ResourceLocation FLOWER=id("calming_flower"),TEA=id("calming_flower_tea");
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("white_lily",path);}
    private static research_progress state(ServerPlayer p){return research_capability.get(p).orElseThrow();}
    private static void enable(ServerPlayer p){research_service.setEditing(p,true,research_service.revision(p));}

    @GameTest(template="empty")
    public static void editingRequiresServerPermissionAndNeverGrantsPermissionFromCreativeMode(GameTestHelper h)
    {
        Fixture guest=new Fixture(h,false);guest.getAbilities().instabuild=true;
        yes(h,!research_service.setEditing(guest,true,research_service.revision(guest)),"创造身份不能授权编辑");
        state(guest).editing=true;
        yes(h,research_service.edit(guest,TEA,true,research_service.revision(guest))==research_service.EditResult.DENIED,"伪造客户端模式仍拒绝");
        var hidden=research_service.buildSnapshot(guest,false);
        yes(h,!hidden.get("editing").getAsBoolean()&&hidden.getAsJsonArray("nodes").size()==4,"未授权不能收到隐藏节点");
        Fixture admin=new Fixture(h,true);enable(admin);
        var all=research_service.buildSnapshot(admin,false);
        yes(h,all.get("editing").getAsBoolean()&&all.getAsJsonArray("nodes").size()==9,"管理员只揭示配置，不完成节点");
        yes(h,!research_service.canCraft(admin,TEA)&&state(admin).completed().isEmpty(),"看到隐藏节点不获得制作或进度");
        admin.admin=false;
        yes(h,research_service.edit(admin,TEA,true,research_service.revision(admin))==research_service.EditResult.DENIED,"每次请求重新检查撤销的权限");
        yes(h,!research_service.buildSnapshot(admin,false).get("editing").getAsBoolean(),"权限撤销退出编辑");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void editCompletionAndResetUpdateRealCraftingAndOtherPlayersStayIndependent(GameTestHelper h)
    {
        Fixture admin=new Fixture(h,true),other=new Fixture(h,false);enable(admin);
        long original=research_service.revision(admin);
        yes(h,research_service.edit(admin,id("missing"),true,original)==research_service.EditResult.INVALID,"无效ID不改变");
        yes(h,research_service.edit(admin,TEA,true,original-1)==research_service.EditResult.STALE,"过期版本不改变");
        admin.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER_TEA.get()));
        yes(h,research_service.edit(admin,TEA,true,original)==research_service.EditResult.APPLIED,"一次直接完成花茶");
        yes(h,state(admin).has(FLOWER)&&state(admin).has(TEA),"自动补齐必要前置");
        yes(h,admin.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==1,"不发额外成品、不扣材料");
        admin.inventoryMenu.getSlot(1).set(new ItemStack(items.CALMING_FLOWER.get()));
        admin.inventoryMenu.getSlot(2).set(new ItemStack(items.CALMING_FLOWER.get()));
        admin.inventoryMenu.getSlot(3).set(PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER));
        yes(h,admin.inventoryMenu.getSlot(0).getItem().is(items.CALMING_FLOWER_TEA.get()),"真正结果槽开放");
        yes(h,research_service.edit(admin,FLOWER,false,research_service.revision(admin))==research_service.EditResult.APPLIED,"重置根节点");
        yes(h,!state(admin).has(FLOWER)&&!state(admin).has(TEA)&&!research_service.canCraft(admin,TEA),"后续进度与真实权限一起撤销");
        yes(h,admin.inventoryMenu.getSlot(0).getItem().isEmpty()&&admin.inventoryMenu.quickMoveStack(admin,0).isEmpty(),"已打开菜单及时清空，Shift也不能取");
        yes(h,!admin.getRecipeBook().contains(TEA),"原版配方书撤销");
        yes(h,admin.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==1,"既有成品不删除、不返还材料");
        yes(h,state(other).completed().isEmpty()&&!research_service.canCraft(other,TEA),"不影响其他玩家");
        research_service.setEditing(admin,false,research_service.revision(admin));
        yes(h,research_service.buildSnapshot(admin,false).getAsJsonArray("nodes").size()==4,"退出编辑重新隐藏花茶");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void resetSurvivesSaveLoginAndOwnInventoryMovesButManualCheckCanRediscover(GameTestHelper h)
    {
        Fixture p=new Fixture(h,true);p.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER.get(),3));
        research_service.discover(p);enable(p);
        research_service.edit(p,TEA,true,research_service.revision(p));
        research_service.edit(p,FLOWER,false,research_service.revision(p));
        var saved=state(p).save();state(p).load(saved);
        research_events.login(new PlayerEvent.PlayerLoggedInEvent(p));
        yes(h,!research_service.discover(p)&&!state(p).has(FLOWER),"重登和轮询不使用旧花撤销重置");
        ItemStack old=p.getInventory().removeItemNoUpdate(0);
        // 主背包内部的移动：数量没变，不算新获得。
        p.getInventory().setItem(20,old);
        research_service.discover(p);
        yes(h,!research_service.discover(p)&&!state(p).has(FLOWER),"主背包格之间的移动不算新获得");
        // 统一主背包口径后：移出主背包（光标/合成格）就是数量 0，放回去才算「重新拿到」。
        p.containerMenu.setCarried(old);p.getInventory().setItem(20,ItemStack.EMPTY);
        research_service.discover(p);
        yes(h,research_service.ownedCount(p,items.CALMING_FLOWER.get())==0,"光标里的不算主背包拥有");
        p.containerMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.getSlot(1).set(old);
        research_service.discover(p);
        yes(h,research_service.ownedCount(p,items.CALMING_FLOWER.get())==0,"合成格里的不算主背包拥有");
        p.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);p.getInventory().setItem(20,old);
        yes(h,research_service.ownedCount(p,items.CALMING_FLOWER.get())==3,"放回主背包后重新计数");
        yes(h,research_service.checkInventory(p)==1&&state(p).has(FLOWER),"主动检查可明确重新完成发现");
        yes(h,!state(p).has(TEA)&&p.getInventory().countItem(items.CALMING_FLOWER.get())==3,"不消耗花、不替代提交条件、不连锁整条研究");
        yes(h,research_service.checkInventory(p)==-1,"短冷却拒绝重复检查");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void newAcquisitionCanRediscoverWithoutReenablingExistingStock(GameTestHelper h)
    {
        Fixture p=new Fixture(h,true);p.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER.get(),3));research_service.discover(p);enable(p);
        research_service.edit(p,FLOWER,false,research_service.revision(p));
        yes(h,!research_service.discover(p),"原库存不发现");
        p.getInventory().add(new ItemStack(items.CALMING_FLOWER.get()));
        yes(h,research_service.discover(p)&&state(p).has(FLOWER),"真实新增数量可以再次发现");
        research_service.edit(p,FLOWER,false,research_service.revision(p));
        research_service.acquired(p,Items.DIRT);
        yes(h,!state(p).has(FLOWER),"无关获得不解除重置");
        research_service.acquired(p,items.CALMING_FLOWER.get());
        yes(h,state(p).has(FLOWER),"明确的新获得入口允许重新发现");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void treeHitRadiusStaysBoundedForSingleNodeAndTinySpacing(GameTestHelper h)
    {
        double inf=Double.POSITIVE_INFINITY;
        // 只有一个节点时半间距是无穷大：命中半径必须仍然是有限的节点半径，不能变成「点哪儿都选中」。
        for(double zoom:new double[]{.1,.25,.5,1,2})
        {
            double r=research_tree_view.hitRadius(zoom,inf);
            yes(h,Double.isFinite(r),"无穷间距下命中半径必须有限，zoom="+zoom);
            yes(h,r<=research_tree_view.HIT_RADIUS_MAX*research_tree_view.clamp(zoom)*2,"命中半径不得超过上限，zoom="+zoom);
            yes(h,r>=research_tree_view.HIT_RADIUS_MIN,"命中半径不得低于下限，zoom="+zoom);
        }
        yes(h,Math.abs(research_tree_view.hitRadius(1,inf)-research_tree_view.HIT_RADIUS_MAX)<1e-9,"100% 时就是节点本体半径");
        yes(h,Math.abs(research_tree_view.hitRadius(.1,inf)-research_tree_view.HIT_RADIUS_MIN)<1e-9,"缩小到阈值以下取最小值 2px");

        // 两个节点靠得很近时，命中半径不超过半间距的 0.9 倍，避免可点区域重叠。
        yes(h,Math.abs(research_tree_view.hitRadius(1,6)-6*.9)<1e-9,"邻近节点限制生效");
        // 异常/无效间距视为「没有邻近节点」，退回本体半径（不能因为 0 间距把可点区域弄成 0）。
        yes(h,research_tree_view.hitRadius(1,0)==research_tree_view.HIT_RADIUS_MAX,"零间距退回本体半径");
        yes(h,research_tree_view.hitRadius(1,-5)==research_tree_view.HIT_RADIUS_MAX,"负间距退回本体半径");
        yes(h,research_tree_view.hitRadius(Double.NaN,inf)<=research_tree_view.HIT_RADIUS_MAX*2+1e-9,"NaN 缩放不放大半径");
        // 单节点时用默认半间距，别把无穷大写到下游。
        yes(h,Double.isFinite(research_tree_view.DEFAULT_SPACING)&&research_tree_view.DEFAULT_SPACING>0,"默认半间距必须是有限正数");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void treeZoomPreservesMouseAnchorAtEveryDisplayThresholdAndResizePreservesCenter(GameTestHelper h)
    {
        research_tree_view view=new research_tree_view();view.centerX=73;view.centerY=-12;
        for(double zoom:new double[]{.1,.25,.5,1,2})
        {
            double x=view.worldX(47,310),y=view.worldY(81,190);
            view.zoomAt(zoom,47,81,310,190);
            yes(h,Math.abs(view.worldX(47,310)-x)<1e-8&&Math.abs(view.worldY(81,190)-y)<1e-8,"每个阈值的鼠标研究坐标不跳变");
            yes(h,view.zoom==zoom,"10/25/50/100/200%实际范围");
            yes(h,view.worldX(400,800)==view.centerX&&view.worldY(300,600)==view.centerY,"改变视口尺寸中心保持");
        }
        view.zoomAt(.001,0,0,100,100);yes(h,view.zoom==.1,"最小10%");
        view.zoomAt(99,0,0,100,100);yes(h,view.zoom==2,"最大200%");
        view.fit(-10,-10,100000,100000,80,80);yes(h,view.zoom==.1,"恢复视图仍限制最小值");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void displayPreferencesReallyPersistWithoutWritingProgressOrItemNbt(GameTestHelper h) throws Exception
    {
        Path directory=Files.createTempDirectory("white-lily-ui-test-");Path file=directory.resolve("preferences.json");
        var prefs=new research_preferences(file);yes(h,!prefs.icons&&!prefs.overlay,"默认展开及侧栏");
        prefs.icons=true;prefs.overlay=true;prefs.save();
        var loaded=new research_preferences(file);yes(h,loaded.icons&&loaded.overlay,"重新读取持久保存的两项客户端偏好");
        String json=Files.readString(file);yes(h,!json.contains("Completed")&&!json.contains("calming_flower"),"显示配置不保存研究数据");
        Files.delete(file);Files.delete(directory);
        h.succeed();
    }
    private static final class Fixture extends ServerPlayer
    {
        boolean admin;
        Fixture(GameTestHelper h,boolean admin)
        {
            super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ResearchEdit"));this.admin=admin;
            connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),this)
            { @Override public void send(Packet<?> p){} @Override public void send(Packet<?> p,PacketSendListener listener){} };
            setHealth(getMaxHealth());
        }
        @Override public boolean hasPermissions(int level){return admin&&level<=2;}
    }
}
