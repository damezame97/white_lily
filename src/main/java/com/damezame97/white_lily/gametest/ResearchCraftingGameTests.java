package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.crafting.prescription_recipes;
import com.damezame97.white_lily.crafting.research_crafting_recipe;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.research.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class ResearchCraftingGameTests
{
    private static final ResourceLocation TEA = ResourceLocation.fromNamespaceAndPath("white_lily", "calming_flower_tea");
    private static research_crafting_recipe tea(GameTestHelper h)
    { return (research_crafting_recipe)h.getLevel().getRecipeManager().byKey(TEA).orElseThrow(); }
    private static void unlock(PlayerFixture player)
    { research_capability.get(player).orElseThrow().complete(TEA); }
    private static CraftingContainer grid(AbstractContainerMenu menu)
    { return (CraftingContainer)menu.getSlot(1).container; }
    private static void putTea(AbstractContainerMenu menu, int batches)
    {
        menu.getSlot(1).set(new ItemStack(items.CALMING_FLOWER.get(),batches));
        menu.getSlot(2).set(new ItemStack(items.CALMING_FLOWER.get(),batches));
        menu.getSlot(3).set(PotionUtils.setPotion(new ItemStack(Items.POTION,batches),Potions.WATER));
    }
    private static CraftingMenu table(GameTestHelper h, PlayerFixture player)
    {
        BlockPos pos=h.absolutePos(new BlockPos(1,1,1));
        h.getLevel().setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu menu=new CraftingMenu(1,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),pos));
        player.containerMenu=menu;
        return menu;
    }

    @GameTest(template="empty")
    public static void researchLocksActualTwoAndThreeSquareMenusBeforeAndDuringPickup(GameTestHelper h)
    {
        for(boolean workbench:new boolean[]{false,true})
        {
            PlayerFixture player=new PlayerFixture(h);
            AbstractContainerMenu menu=workbench?table(h,player):player.inventoryMenu;
            player.containerMenu=menu;
            putTea(menu,2);
            yes(h,menu.getSlot(0).getItem().isEmpty(),"未研究的真实2x2/3x3网格没有结果");
            yes(h,!tea(h).matches(grid(menu),h.getLevel()),"手动摆料不能绕过研究");
            yes(h,menu.quickMoveStack(player,0).isEmpty(),"直接Shift调用不能制作");
            menu.clicked(0,0,ClickType.PICKUP,player);
            yes(h,menu.getCarried().isEmpty()&&menu.getSlot(1).getItem().getCount()==2,"锁定取出不扣材料");

            // 模拟旧菜单残留的结果；必须在转移给玩家之前拒绝，不能依赖事后合成事件。
            ((ResultContainer)menu.getSlot(0).container).setRecipeUsed(tea(h));
            menu.getSlot(0).set(new ItemStack(items.CALMING_FLOWER_TEA.get()));
            menu.clicked(0,0,ClickType.PICKUP,player);
            yes(h,menu.getCarried().isEmpty()&&!menu.getSlot(0).mayPickup(player),"过期结果普通取出被阻止");
            yes(h,menu.quickMoveStack(player,0).isEmpty()&&player.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==0,"过期结果Shift在搬运前拒绝");
            yes(h,menu.getSlot(1).getItem().getCount()==2&&menu.getSlot(3).getItem().getCount()==2,"拒绝无副作用");
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void researchUnlockRefreshesImmediatelyAndKeepsPlayersIndependent(GameTestHelper h)
    {
        PlayerFixture unlocked=new PlayerFixture(h), locked=new PlayerFixture(h);
        CraftingMenu unlockedMenu=table(h,unlocked),lockedMenu=table(h,locked);
        putTea(unlockedMenu,3);putTea(lockedMenu,3);
        unlock(unlocked);research_crafting_gate.refresh(unlocked);
        yes(h,unlockedMenu.getSlot(0).getItem().is(items.CALMING_FLOWER_TEA.get()),"不重新放料即刻刷新已解锁玩家结果");
        yes(h,lockedMenu.getSlot(0).getItem().isEmpty(),"另一玩家不继承权限或结果");
        unlockedMenu.clicked(0,0,ClickType.PICKUP,unlocked);
        yes(h,unlockedMenu.getCarried().is(items.CALMING_FLOWER_TEA.get())&&unlockedMenu.getSlot(1).getItem().getCount()==2,"真实普通取出消耗一批");
        unlockedMenu.setCarried(ItemStack.EMPTY);
        unlockedMenu.clicked(0,0,ClickType.QUICK_MOVE,unlocked);
        yes(h,unlocked.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==2,"真实Shift连续制作剩余两批");
        yes(h,grid(unlockedMenu).isEmpty()&&unlocked.getInventory().countItem(Items.GLASS_BOTTLE)==0,"制作消耗水瓶但不提前返空瓶");
        locked.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER_TEA.get()));
        research_service.discover(locked);
        yes(h,!research_service.canCraft(locked,TEA),"赠送成品不授予制作权限");
        yes(h,!research_capability.get(locked).orElseThrow().has(TEA),"赠送成品不完成研究");

        PlayerFixture small=new PlayerFixture(h);unlock(small);putTea(small.inventoryMenu,2);
        small.inventoryMenu.clicked(0,0,ClickType.QUICK_MOVE,small);
        yes(h,small.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==2&&grid(small.inventoryMenu).isEmpty(),"已解锁2x2同样支持原版Shift批量路径");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void researchRecipeBookDeniesPrematureAwardsAndSanitizesLegacyProgress(GameTestHelper h)
    {
        PlayerFixture player=new PlayerFixture(h);
        CraftingMenu menu=table(h,player);
        var recipe=tea(h);
        yes(h,player.awardRecipes(List.of(recipe))==0&&!player.getRecipeBook().contains(recipe),"配方通知/命令不能提前授予受限配方");
        player.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER.get(),2));
        player.getInventory().setItem(1,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER));
        new ServerPlaceRecipe<CraftingContainer>(menu).recipeClicked(player,recipe,false);
        yes(h,grid(menu).isEmpty(),"未解锁配方书请求不填充也不制作");
        player.getRecipeBook().add(recipe);player.getRecipeBook().addHighlight(recipe);
        new ServerPlaceRecipe<CraftingContainer>(menu).recipeClicked(player,recipe,false);
        yes(h,grid(menu).isEmpty()&&player.getInventory().countItem(items.CALMING_FLOWER.get())==2,
                "即使残留旧已知配方，填充入口仍按玩家研究权限拒绝");
        player.getRecipeBook().sendInitialRecipeBook(player);
        yes(h,!player.getRecipeBook().contains(recipe)&&!player.getRecipeBook().willHighlight(recipe),"旧存档已知配方与高亮在初始同步前清除");
        unlock(player);
        yes(h,player.awardRecipes(List.of(recipe))==1&&player.getRecipeBook().contains(recipe),"研究完成后正式进入原版配方书");
        new ServerPlaceRecipe<CraftingContainer>(menu).recipeClicked(player,recipe,false);
        // Forge支持NBT配料；真实配方书填充后仍必须由matches校验普通水瓶及玩家权限。
        yes(h,menu.getSlot(0).getItem().is(items.CALMING_FLOWER_TEA.get()),"已研究配方书正常填充普通水瓶路线");
        menu.clicked(0,0,ClickType.PICKUP,player);
        yes(h,menu.getCarried().is(items.CALMING_FLOWER_TEA.get()),"配方书填充后可正常取出");
        PlayerFixture wrongPotion=new PlayerFixture(h);unlock(wrongPotion);wrongPotion.awardRecipes(List.of(recipe));
        CraftingMenu wrongMenu=table(h,wrongPotion);
        wrongPotion.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER.get(),2));
        wrongPotion.getInventory().setItem(1,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.HEALING));
        new ServerPlaceRecipe<CraftingContainer>(wrongMenu).recipeClicked(wrongPotion,recipe,false);
        yes(h,wrongMenu.getSlot(0).getItem().isEmpty()&&wrongPotion.getInventory().getItem(1).getCount()==1,
                "配方书同样不能把治疗药水当成普通水瓶，且不消耗错误药水");

        PlayerFixture batch=new PlayerFixture(h);unlock(batch);batch.awardRecipes(List.of(recipe));
        CraftingMenu batchMenu=table(h,batch);
        batch.getInventory().setItem(0,new ItemStack(items.CALMING_FLOWER.get(),6));
        ItemStack water=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER);
        yes(h,water.getMaxStackSize()==1,"保持当前原版普通水瓶不可堆叠规则");
        for(int slot=1;slot<=3;slot++) batch.getInventory().setItem(slot,water.copy());
        // 重复使用同一个原生放置器，验证每次请求重置配料游标；placeAll仍尊重水瓶上限1。
        ServerPlaceRecipe<CraftingContainer> placement=new ServerPlaceRecipe<>(batchMenu);
        for(int produced=1;produced<=3;produced++)
        {
            placement.recipeClicked(batch,recipe,true);
            ItemStack filledWater=batchMenu.getSlot(3).getItem();
            yes(h,batchMenu.getSlot(1).getItem().getCount()==1&&batchMenu.getSlot(2).getItem().getCount()==1
                            &&filledWater.getCount()==1&&ItemStack.isSameItemSameTags(filledWater,water),
                    "placeAll只填一批，正确保留水瓶NBT且不制造超过物品上限的堆叠");
            batchMenu.clicked(0,0,ClickType.QUICK_MOVE,batch);
            yes(h,batch.getInventory().countItem(items.CALMING_FLOWER_TEA.get())==produced&&grid(batchMenu).isEmpty(),
                    "配方书全部填充+Shift每次只执行可用水瓶的一批");
            yes(h,batch.getInventory().countItem(Items.POTION)==3-produced
                            &&batch.getInventory().countItem(items.CALMING_FLOWER.get())==6-produced*2,
                    "三次真实配方书请求按实际产量消耗，不复制或吞材料");
        }
        yes(h,batch.getInventory().countItem(Items.GLASS_BOTTLE)==0,"批量制作花茶仍不提前返回空瓶");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void researchRecipePreservesExactWaterNbtNetworkAndUnownedGridFailsClosed(GameTestHelper h)
    {
        PlayerFixture player=new PlayerFixture(h);unlock(player);
        CraftingMenu menu=table(h,player);putTea(menu,1);
        var original=tea(h);
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            prescription_recipes.RESEARCH_CRAFTING.get().toNetwork(buffer,original);
            var copy=prescription_recipes.RESEARCH_CRAFTING.get().fromNetwork(TEA,buffer);
            yes(h,copy!=null&&copy.matches(grid(menu),h.getLevel()),"网络同步保留配料与权限匹配");
            ItemStack[] invalid={PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.AWKWARD),
                    PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.HEALING),
                    PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION),Potions.WATER),
                    PotionUtils.setPotion(new ItemStack(Items.LINGERING_POTION),Potions.WATER)};
            for(ItemStack stack:invalid)
            {
                menu.getSlot(3).set(stack);
                yes(h,!copy.matches(grid(menu),h.getLevel())&&menu.getSlot(0).getItem().isEmpty(),"受限配方仍拒绝其他药水/喷溅/滞留");
            }
            AbstractContainerMenu anonymous=new AbstractContainerMenu(null,9)
            {
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int i){return ItemStack.EMPTY;}
            };
            CraftingContainer noOwner=new TransientCraftingContainer(anonymous,2,2);
            noOwner.setItem(0,new ItemStack(items.CALMING_FLOWER.get()));noOwner.setItem(1,new ItemStack(items.CALMING_FLOWER.get()));
            noOwner.setItem(2,PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.WATER));
            yes(h,!copy.matches(noOwner,h.getLevel()),"无明确玩家归属的合成网格安全拒绝，不借用其他玩家权限");
        }
        finally {buffer.release();}
        h.succeed();
    }

    private static final class PlayerFixture extends ServerPlayer
    {
        PlayerFixture(GameTestHelper h)
        {
            super(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ResearchCraft"));
            connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),this)
            {
                @Override public void send(Packet<?> packet){ }
                @Override public void send(Packet<?> packet,PacketSendListener listener){ }
            };
            setHealth(getMaxHealth());getAbilities().invulnerable=false;
        }
    }
}
