package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.block.*;
import com.damezame97.white_lily.crafting.*;
import com.damezame97.white_lily.item.items;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily")
@PrefixGameTestTemplate(false)
@SuppressWarnings({"unused", "resource"})
public final class HempGameTests
{
    private static ResourceLocation id(String path) { return new ResourceLocation("white_lily", path); }
    private static CraftingRecipe recipe(GameTestHelper h, String name)
    { return (CraftingRecipe)h.getLevel().getRecipeManager().byKey(id(name)).orElseThrow(); }

    @GameTest(template="empty")
    public static void sixFinalRecipesAndNoOldMaterialBypasses(GameTestHelper h)
    {
        Menu cloth=new Menu(h);
        for(int row=0;row<3;row++)
        {
            cloth.grid().clearContent();
            for(int col=0;col<3;col++)cloth.getSlot(1+row*3+col).set(new ItemStack(items.HEMP_STALK.get()));
            checkRecipe(h,cloth,"coarse_cloth",items.COARSE_CLOTH.get(),2);
        }
        cloth.grid().clearContent();
        for(int row=0;row<3;row++)cloth.getSlot(1+row*3).set(new ItemStack(items.HEMP_STALK.get()));
        yes(h,!recipe(h,"coarse_cloth").matches(cloth.grid(),h.getLevel()),"麻秆必须水平排列");
        check(h,"thin_cloth_strip",items.THIN_CLOTH_STRIP.get(),3,items.COARSE_CLOTH.get(),Items.SHEARS);
        check(h,"beeswax_block",items.BEESWAX_BLOCK.get(),3,Items.HONEYCOMB,Items.HONEY_BOTTLE);
        check(h,"charcoal_powder",items.CHARCOAL_POWDER.get(),4,Items.CHARCOAL);
        check(h,"blank_label",items.BLANK_LABEL.get(),4,Items.PAPER,Items.STRING);
        for(Item log:new Item[]{Items.OAK_LOG,Items.SPRUCE_LOG,Items.BIRCH_LOG,Items.JUNGLE_LOG,
                Items.ACACIA_LOG,Items.DARK_OAK_LOG,Items.MANGROVE_LOG,Items.CHERRY_LOG})
            check(h,"cork_stopper",items.CORK_STOPPER.get(),8,log,Items.SHEARS);
        Menu invalid=new Menu(h);
        for(Item log:new Item[]{Items.STRIPPED_OAK_LOG,Items.STRIPPED_CHERRY_LOG,Items.OAK_WOOD,Items.CHERRY_WOOD,
                Items.CRIMSON_STEM,Items.WARPED_STEM,Items.BAMBOO_BLOCK,Items.OAK_PLANKS,Items.OAK_LEAVES})
        {
            invalid.grid().clearContent();invalid.getSlot(1).set(new ItemStack(log));invalid.getSlot(2).set(new ItemStack(Items.SHEARS));
            yes(h,!recipe(h,"cork_stopper").matches(invalid.grid(),h.getLevel()),"排除非法树皮材料 "+log);
        }
        invalid.grid().clearContent();invalid.getSlot(1).set(new ItemStack(Items.COAL));
        yes(h,!recipe(h,"charcoal_powder").matches(invalid.grid(),h.getLevel()),"煤炭不能代替木炭");
        assertNoOutput(h,items.COARSE_CLOTH.get(),Items.STRING,Items.STRING,Items.STRING,Items.STRING);
        assertNoOutput(h,items.THIN_CLOTH_STRIP.get(),items.COARSE_CLOTH.get());
        assertNoOutput(h,items.BEESWAX_BLOCK.get(),Items.HONEYCOMB);
        yes(h,items.HEMP_SEEDS.get().getMaxStackSize()==64&&items.HEMP_STALK.get().getMaxStackSize()==64,"两种新材料堆叠64");
        h.succeed();
    }

    private static void check(GameTestHelper h,String name,Item output,int count,Item...inputs)
    {
        Menu menu=new Menu(h);
        for(int i=0;i<inputs.length;i++)menu.getSlot(9-i).set(new ItemStack(inputs[i]));
        checkRecipe(h,menu,name,output,count);
    }
    private static void checkRecipe(GameTestHelper h,Menu menu,String name,Item output,int count)
    {
        var matches=h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(r->r.matches(menu.grid(),h.getLevel())).toList();
        yes(h,matches.stream().anyMatch(r->r.getId().equals(id(name))),"最终配方可匹配 "+name);
        if(matches.size()!=1)System.out.println("HEMP RECIPE CONFLICT "+name+": "+matches.stream().map(Recipe::getId).toList());
        yes(h,menu.getSlot(0).getItem().is(output)&&menu.getSlot(0).getItem().getCount()==count,"真实配方管理器选择正确产出 "+name);
    }
    private static void assertNoOutput(GameTestHelper h,Item output,Item...inputs)
    {
        Menu menu=new Menu(h);
        for(int i=0;i<inputs.length;i++)menu.getSlot(1+i).set(new ItemStack(inputs[i]));
        yes(h,h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                .noneMatch(r->r.matches(menu.grid(),h.getLevel())&&r.getResultItem(h.getLevel().registryAccess()).is(output)),"旧低成本旁路不存在");
    }

    @GameTest(template="empty")
    public static void shearsPreviewPreservesNbtAndActualPickupDamagesExactlyOnce(GameTestHelper h)
    {
        for(int damage:new int[]{0,70,237})
        {
            Menu menu=new Menu(h);
            ItemStack tool=decorated(damage);
            var tag=tool.getTag().copy();
            menu.getSlot(4).set(tool);menu.getSlot(8).set(new ItemStack(items.COARSE_CLOTH.get(),3));
            var recipe=recipe(h,"thin_cloth_strip");
            for(int preview=0;preview<5;preview++)
            {
                recipe.assemble(menu.grid(),h.getLevel().registryAccess());recipe.getRemainingItems(menu.grid());
                yes(h,Objects.equals(tool.getTag(),tag),"预览不修改剪刀NBT");
            }
            menu.clicked(0,0,ClickType.PICKUP,menu.player);
            yes(h,menu.getCarried().is(items.THIN_CLOTH_STRIP.get())&&menu.getCarried().getCount()==3,"一次产出3");
            yes(h,menu.getSlot(8).getItem().getCount()==2,"一次只消耗1片布");
            ItemStack returned=menu.getSlot(4).getItem();
            if(damage==237)yes(h,returned.isEmpty(),"最后一次仍产出且剪刀破碎");
            else
            {
                var expected=tag.copy();expected.putInt("Damage",damage+1);
                yes(h,returned.is(Items.SHEARS)&&returned.getCount()==1&&expected.equals(returned.getTag()),"固定+1损伤，完整保留名称附魔和其他NBT");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void shiftCraftingStopsAtToolBreakAndFullInventoryCannotCraftForFree(GameTestHelper h)
    {
        for(boolean cork:new boolean[]{false,true})
        {
            Menu menu=new Menu(h);
            menu.getSlot(1).set(new ItemStack(cork?Items.OAK_LOG:items.COARSE_CLOTH.get(),7));
            menu.getSlot(2).set(decorated(236));
            menu.clicked(0,0,ClickType.QUICK_MOVE,menu.player);
            yes(h,menu.player.getInventory().countItem(cork?items.CORK_STOPPER.get():items.THIN_CLOTH_STRIP.get())==(cork?16:6),"Shift精确执行剩余2次耐久");
            yes(h,menu.getSlot(1).getItem().getCount()==5&&menu.getSlot(2).getItem().isEmpty(),"工具破碎后停止，不吞剩余材料");
        }
        Menu full=new Menu(h);
        for(int i=0;i<36;i++)full.player.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
        full.getSlot(1).set(new ItemStack(items.COARSE_CLOTH.get(),3));full.getSlot(2).set(decorated(12));
        full.clicked(0,0,ClickType.QUICK_MOVE,full.player);
        yes(h,full.getSlot(2).getItem().getDamageValue()==12&&full.getSlot(1).getItem().getCount()==3,"背包无空间取不走结果，不扣耐久");
        full.clicked(0,0,ClickType.PICKUP,full.player);
        yes(h,full.getCarried().getCount()==3&&full.getSlot(2).getItem().getDamageValue()==13,"普通取出时剪刀回原槽，满背包也不吞工具");
        full.setCarried(ItemStack.EMPTY);
        full.clicked(0,0,ClickType.PICKUP,full.player);
        yes(h,full.getSlot(2).getItem().getDamageValue()==14&&full.getSlot(1).getItem().getCount()==1,"连续取出每次只扣一点");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void honeyBatchReturnsBottlesAndToolRecipeSurvivesNetworkRoundTrip(GameTestHelper h)
    {
        Menu menu=new Menu(h);
        menu.getSlot(1).set(new ItemStack(Items.HONEYCOMB,4));menu.getSlot(2).set(new ItemStack(Items.HONEY_BOTTLE,4));
        menu.clicked(0,0,ClickType.QUICK_MOVE,menu.player);
        yes(h,menu.player.getInventory().countItem(items.BEESWAX_BLOCK.get())==12,"Shift蜂蜡产量12");
        int bottles=menu.player.getInventory().countItem(Items.GLASS_BOTTLE);
        for(int i=1;i<=9;i++)if(menu.getSlot(i).getItem().is(Items.GLASS_BOTTLE))bottles+=menu.getSlot(i).getItem().getCount();
        yes(h,bottles==4,"四个蜂蜜瓶恰好返四个玻璃瓶");
        for(String name:new String[]{"thin_cloth_strip","cork_stopper"})
        {
            var original=(durable_tool_recipe)recipe(h,name);
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
            try
            {
                prescription_recipes.DURABLE_TOOL.get().toNetwork(buffer,original);
                var copy=prescription_recipes.DURABLE_TOOL.get().fromNetwork(original.getId(),buffer);
                yes(h,copy.getId().equals(original.getId())&&copy.category()==original.category()&&copy.getIngredients().size()==2,"配方书分类及网络配料完整");
                Menu grid=new Menu(h);grid.getSlot(1).set(new ItemStack(name.equals("thin_cloth_strip")?items.COARSE_CLOTH.get():Items.CHERRY_LOG));
                grid.getSlot(2).set(decorated(50));
                yes(h,copy.matches(grid.grid(),h.getLevel())&&copy.getRemainingItems(grid.grid()).get(1).getDamageValue()==51,"同步后保留工具加工行为");
            }
            finally{buffer.release();}
        }
        h.succeed();
    }

    @GameTest(template="empty")
    public static void farmlandGrowthBoneMealAndWildSoilsAreSeparate(GameTestHelper h)
    {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,30,2));
        level.setBlock(pos.east(),Blocks.GLOWSTONE.defaultBlockState(),3);
        // GameTest 世界不保证天空光，等待服务端处理真实光源后再检查。
        h.runAfterDelay(20,()-> {
            var crop=(hemp_crop)medical_blocks.HEMP_CROP.get();var wild=medical_blocks.WILD_HEMP.get();
            for(Block soil:new Block[]{Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.COARSE_DIRT,Blocks.PODZOL,Blocks.OAK_LEAVES,Blocks.SAND})
            {
                level.setBlock(pos.below(),soil.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                yes(h,!crop.defaultBlockState().canSurvive(level,pos),"玩家作物只能种耕地");
                yes(h,wild.defaultBlockState().canSurvive(level,pos)==(soil==Blocks.DIRT||soil==Blocks.GRASS_BLOCK),"野生仅接受草方块/泥土");
            }
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            yes(h,crop.defaultBlockState().canSurvive(level,pos),"作物可在耕地生存: soil="+level.getBlockState(pos.below())+", light="+level.getRawBrightness(pos,0)+", sky="+level.canSeeSky(pos));
            for(int stage=0;stage<3;stage++)
            {
                level.setBlock(pos,crop.getStateForAge(stage),3);
                crop.randomTick(crop.getStateForAge(stage),level,pos,new GrowthRandom(3));
                yes(h,crop.getAge(level.getBlockState(pos))==stage,"3/7额外筛选未通过不生长");
                crop.randomTick(crop.getStateForAge(stage),level,pos,new GrowthRandom(2));
                yes(h,crop.getAge(level.getBlockState(pos))==stage+1,"3/7通过每次只长一阶段");
            }
            ItemStack meal=new ItemStack(Items.BONE_MEAL,3);
            yes(h,!BoneMealItem.growCrop(meal,level,pos)&&meal.getCount()==3,"成熟麻不能骨粉复制或消耗");
            level.setBlock(pos,crop.getStateForAge(0),3);
            yes(h,BoneMealItem.growCrop(meal,level,pos)&&crop.getAge(level.getBlockState(pos))>0,"骨粉促进未成熟麻生长");
            level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,wild.defaultBlockState(),3);
            int before=meal.getCount();
            yes(h,!BoneMealItem.growCrop(meal,level,pos)&&meal.getCount()==before&&!wild.defaultBlockState().isRandomlyTicking(),"野生麻无骨粉复制或随机生长");
            yes(h,!(items.HEMP_STALK.get() instanceof BlockItem)&&items.HEMP_SEEDS.get() instanceof ItemNameBlockItem,"麻秆不是种植物品");
            yes(h,crop.defaultBlockState().getCollisionShape(level,pos).isEmpty(),"作物无实体碰撞");
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.east(),Blocks.AIR.defaultBlockState(),3);h.succeed();
        });
    }

    @GameTest(template="empty")
    public static void cropLootTablesHaveUniformBoundedDropsAndNoFortuneMultiplier(GameTestHelper h) throws ReflectiveOperationException
    {
        var level=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(2,30,2));
        var crop=(hemp_crop)medical_blocks.HEMP_CROP.get();
        ItemStack fortune=new ItemStack(Items.DIAMOND_HOE);fortune.enchant(Enchantments.BLOCK_FORTUNE,3);
        for(int stage=0;stage<4;stage++)
        {
            for(ItemStack tool:new ItemStack[]{ItemStack.EMPTY,fortune,new ItemStack(Items.SHEARS)})
            {
                var drops=Block.getDrops(crop.getStateForAge(stage),level,pos,null,h.makeMockSurvivalPlayer(),tool);
                checkDrops(h,drops,stage==3);
            }
        }
        checkDrops(h,Block.getDrops(medical_blocks.WILD_HEMP.get().defaultBlockState(),level,pos,null),true);
        // 直接运行掉落表，强制随机源取每个均匀分布端点，而不是靠抽样频率证明概率。
        var table=level.getServer().getLootData().getLootTable(crop.getLootTable());
        for(int roll=0;roll<=1;roll++)
        {
            var parameters=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,pos.getCenter())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,crop.getStateForAge(3))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,ItemStack.EMPTY)
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
            var builder=new net.minecraft.world.level.storage.loot.LootContext.Builder(parameters);
            var randomField=builder.getClass().getDeclaredField("random");randomField.setAccessible(true);
            randomField.set(builder,new EndpointRandom(roll));
            var context=builder.create(null);
            List<ItemStack> drops=new ArrayList<>();table.getRandomItems(context,drops::add);
            yes(h,total(drops,items.HEMP_STALK.get())==2+roll&&total(drops,items.HEMP_SEEDS.get())==1+roll,"均匀数量两端正确");
        }
        h.succeed();
    }
    private static void checkDrops(GameTestHelper h,List<ItemStack> drops,boolean mature)
    {
        int stalk=total(drops,items.HEMP_STALK.get()),seeds=total(drops,items.HEMP_SEEDS.get());
        yes(h,mature?stalk>=2&&stalk<=3&&seeds>=1&&seeds<=2:stalk==0&&seeds==1,"成熟/未成熟掉落范围，无时运加成");
        yes(h,drops.stream().allMatch(s->s.is(items.HEMP_SEEDS.get())||s.is(items.HEMP_STALK.get())),"不掉落额外方块或小麦");
    }
    private static int total(List<ItemStack> drops,Item item){return drops.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static ItemStack decorated(int damage)
    {
        ItemStack tool=new ItemStack(Items.SHEARS);tool.setDamageValue(damage);
        tool.setHoverName(Component.literal("Hemp test shears"));tool.enchant(Enchantments.UNBREAKING,3);
        tool.getOrCreateTag().putString("custom_data","preserve me");return tool;
    }
    private static final class GrowthRandom extends LegacyRandomSource
    {
        final int stageRoll;
        GrowthRandom(int roll){super(0);stageRoll=roll;}
        @Override public int nextInt(int bound){return bound==7?stageRoll:0;}
    }
    private static final class EndpointRandom extends LegacyRandomSource
    {
        final int high;
        EndpointRandom(int high){super(0);this.high=high;}
        @Override public int nextInt(int bound){return high==0?0:bound-1;}
    }
    private static final class Menu extends CraftingMenu
    {
        final Player player;
        Menu(GameTestHelper h){this(h.makeMockSurvivalPlayer());}
        Menu(Player p){super(1,p.getInventory());player=p;}
        CraftingContainer grid(){return(CraftingContainer)getSlot(1).container;}
        @Override public void slotsChanged(Container container)
        {
            if(player!=null)getSlot(0).set(player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid(),player.level())
                    .map(recipe->recipe.assemble(grid(),player.level().registryAccess())).orElse(ItemStack.EMPTY));
        }
    }
}
