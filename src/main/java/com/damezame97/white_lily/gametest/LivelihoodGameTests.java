package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.item.basic_material_item;
import com.damezame97.white_lily.item.creative_tabs;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.white_lily;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder(white_lily.MODID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public final class LivelihoodGameTests
{
    @GameTest(template="empty")
    public static void threeTabsPublicSearchOrderAndMaterialDescriptions(GameTestHelper h)
    {
        List<CreativeModeTab> sorted = CreativeModeTabRegistry.getSortedCreativeModeTabs();
        CreativeModeTab delicacies = creative_tabs.MISGUIDED_DELICACIES.get();
        CreativeModeTab livelihood = creative_tabs.MISGUIDED_LIVELIHOOD.get();
        CreativeModeTab tests = creative_tabs.MEDICAL_TESTS.get();
        yes(h,sorted.indexOf(livelihood)==sorted.indexOf(delicacies)+1 && sorted.indexOf(tests)==sorted.indexOf(livelihood)+1,
                "本模组三栏相邻有序：珍馐、生计、测试");
        yes(h,TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(livelihood.getDisplayName().getStyle().getColor())
                && livelihood.getIconItem().is(items.HERBAL_POUCH.get()),"生计绿色标题和真实布包图标");
        var parameters = new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess());
        for(var tab:List.of(delicacies,livelihood,tests)){tab.buildContents(parameters);yes(h,tab.shouldDisplay(),"非空栏正常显示");}
        List<Item> expected = List.of(items.HERBAL_POUCH.get(),items.INVESTIGATION_HANDBOOK.get(),items.SIMPLE_PROCESSING_TABLE.get(),items.STONE_MORTAR.get(),
                items.HEMP_SEEDS.get(),items.HEMP_STALK.get(),items.BITTERLEAF.get(),items.YARROW.get(),items.CALMING_FLOWER.get(),
                items.PLANT_FIBER.get(),items.COARSE_CLOTH.get(),items.THIN_CLOTH_STRIP.get(),items.WOODEN_TRAY.get(),items.CORK_STOPPER.get(),
                items.BEESWAX_BLOCK.get(),items.CHARCOAL_POWDER.get(),items.CLAY_BOWL.get(),items.GLASS_TUBE.get(),items.FILTER_PAPER.get(),
                items.DESICCANT_PACKET.get(),items.BLANK_LABEL.get(),items.BLANK_PRESCRIPTION.get(),items.REVEALED_PRESCRIPTION.get(),
                items.SIMPLE_BANDAGE.get(),items.CLEAN_FINE_BANDAGE.get(),items.BITTERLEAF_PASTE.get(),items.YARROW_DRESSING.get(),
                items.CALMING_FLOWER_TEA.get(),items.HERBAL_RESIDUE.get());
        yes(h,livelihood.getDisplayItems().stream().map(ItemStack::getItem).toList().equals(expected),"生计29项顺序完整，手册靠前且原相对顺序不变");
        yes(h,delicacies.getDisplayItems().stream().map(ItemStack::getItem).toList().equals(
                List.of(items.MAGIC_APPLE.get(),items.LOW_QUALITY_POTION.get(),items.STORED_SUFFERING.get())),"珍馐仅保留三件原物品");
        yes(h,tests.getDisplayItems().size()==7 && tests.getIconItem().is(items.POLLUTION_DETECTOR.get()),"测试栏保留七件和原图标");
        HashSet<Item> publicItems = new HashSet<>();
        for(var tab:List.of(delicacies,livelihood,tests))for(var stack:tab.getDisplayItems())
        {
            yes(h,publicItems.add(stack.getItem()),"三栏无重复");
            yes(h,tab.getSearchTabDisplayItems().contains(stack),"公开物品进入创造搜索汇总");
        }
        for(var hidden:List.of(items.TAB_ICON.get(),items.DELAYED_HEALING_ICON.get(),items.FLESH_RING.get(),items.IMPROVE_BODY.get(),items.INVALID_PLACEHOLDER.get(),items.FATE_COMPASS.get()))
            yes(h,!publicItems.contains(hidden),"原隐藏图标和绑定饰品不暴露");
        for(var item:List.of(items.PLANT_FIBER.get(),items.WOODEN_TRAY.get(),items.CLAY_BOWL.get(),items.GLASS_TUBE.get(),items.FILTER_PAPER.get(),
                items.DESICCANT_PACKET.get(),items.HERBAL_RESIDUE.get(),items.HERBAL_POUCH.get()))
        {
            ItemStack stack=new ItemStack(item);yes(h,stack.getMaxStackSize()==64&&!stack.isDamageableItem()&&!stack.isEdible()&&!stack.hasFoil(),"新增普通材料定义");
            yes(h,stack.getHoverName().getStyle().getColor()==null,"栏标题绿色不污染物品名");
            List<Component> lines=new ArrayList<>();((basic_material_item)item).appendDescription(lines,true);
            for(var line:lines)yes(h,TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(line.getStyle().getColor())
                    &&!line.getString().contains("item.white_lily."),"展开说明为普通灰色且语言键已解析");
        }
        ItemStack mortar=new ItemStack(items.STONE_MORTAR.get());
        yes(h,mortar.getMaxStackSize()==1&&mortar.getMaxDamage()==128,"研钵堆叠1、真实耐久128");
        List<Component> lines=new ArrayList<>();items.SIMPLE_PROCESSING_TABLE.get().appendHoverText(new ItemStack(items.SIMPLE_PROCESSING_TABLE.get()),h.getLevel(),lines,TooltipFlag.Default.NORMAL);
        yes(h,lines.size()==1,"方块物品默认沿用单条Shift提示");
        h.succeed();
    }

    @GameTest(template="empty")
    public static void newCraftingRecipesExactMaterialsAndNoPouchRecipe(GameTestHelper h)
    {
        check(h,"simple_processing_table",items.SIMPLE_PROCESSING_TABLE.get(),1,
                Items.OAK_PLANKS,items.WOODEN_TRAY.get(),Items.BIRCH_PLANKS,Items.BIRCH_PLANKS,null,Items.OAK_PLANKS,Items.STICK,null,Items.STICK);
        check(h,"wooden_tray",items.WOODEN_TRAY.get(),1,Items.OAK_SLAB,Items.BIRCH_SLAB,Items.CHERRY_SLAB);
        check(h,"stone_mortar",items.STONE_MORTAR.get(),1,Items.STONE,Items.STICK,Items.COBBLESTONE,null,Items.STONE);
        check(h,"clay_bowl",items.CLAY_BOWL.get(),4,Items.BRICK,Items.BRICK,Items.BRICK);
        check(h,"glass_tube",items.GLASS_TUBE.get(),6,Items.GLASS,Items.GLASS,Items.GLASS);
        check(h,"filter_paper",items.FILTER_PAPER.get(),4,Items.PAPER,items.PLANT_FIBER.get());
        check(h,"desiccant_packet",items.DESICCANT_PACKET.get(),2,Items.PAPER,Items.SAND,items.CHARCOAL_POWDER.get());
        check(h,"coarse_cloth_from_fiber",items.COARSE_CLOTH.get(),1,items.PLANT_FIBER.get(),items.PLANT_FIBER.get());
        check(h,"coarse_cloth",items.COARSE_CLOTH.get(),2,items.HEMP_STALK.get(),items.HEMP_STALK.get(),items.HEMP_STALK.get());
        reject(h,"wooden_tray",Items.STONE_SLAB,Items.STONE_SLAB,Items.STONE_SLAB);
        reject(h,"clay_bowl",Items.BRICK,null,Items.BRICK,null,Items.BRICK);
        yes(h,h.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                grid(h,Items.BRICK,null,Items.BRICK,null,Items.BRICK),h.getLevel()).orElseThrow().getResultItem(h.getLevel().registryAccess()).is(Items.FLOWER_POT),
                "原版V形红砖仍制作花盆，不被本模组覆盖");
        reject(h,"stone_mortar",Items.ANDESITE,Items.STICK,Items.COBBLESTONE,null,Items.STONE);
        reject(h,"glass_tube",Items.GLASS_PANE,Items.GLASS_PANE,Items.GLASS_PANE);
        reject(h,"glass_tube",Items.WHITE_STAINED_GLASS,Items.WHITE_STAINED_GLASS,Items.WHITE_STAINED_GLASS);
        reject(h,"glass_tube",Items.TINTED_GLASS,Items.TINTED_GLASS,Items.TINTED_GLASS);
        reject(h,"desiccant_packet",Items.PAPER,Items.RED_SAND,items.CHARCOAL_POWDER.get());
        yes(h,h.getLevel().getRecipeManager().getRecipes().stream().noneMatch(r->r.getResultItem(h.getLevel().registryAccess()).is(items.HERBAL_POUCH.get())),"布包配方待定，未编造获取路线");
        for(String name:List.of("bitterleaf_paste","yarrow_dressing"))yes(h,h.getLevel().getRecipeManager().byKey(id(name)).isEmpty(),"旧药品普通配方已移除");
        for(String locale:List.of("zh_cn","en_us"))
        {
            try(var input=LivelihoodGameTests.class.getResourceAsStream("/assets/white_lily/lang/"+locale+".json"))
            {
                var lang=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                yes(h,lang.has(creative_tabs.MISGUIDED_LIVELIHOOD_TITLE_KEY)&&!lang.get(creative_tabs.MISGUIDED_LIVELIHOOD_TITLE_KEY).getAsString().contains("§"),"新增标题翻译不含颜色控制符");
                for(Item item:List.of(items.PLANT_FIBER.get(),items.WOODEN_TRAY.get(),items.STONE_MORTAR.get(),items.CLAY_BOWL.get(),items.GLASS_TUBE.get(),
                        items.FILTER_PAPER.get(),items.DESICCANT_PACKET.get(),items.HERBAL_RESIDUE.get(),items.HERBAL_POUCH.get(),items.SIMPLE_PROCESSING_TABLE.get()))
                {yes(h,lang.has(item.getDescriptionId()),"中英名称完整");for(int line=1;line<=3;line++)yes(h,lang.has(item.getDescriptionId()+".description."+line),"中英说明完整");}
            }catch(java.io.IOException error){throw new RuntimeException(error);}
        }
        h.succeed();
    }

    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("white_lily",path);}
    private static CraftingContainer grid(GameTestHelper h,Item...inputs)
    {
        var menu=new net.minecraft.world.inventory.CraftingMenu(1,h.makeMockPlayer().getInventory());
        for(int index=0;index<inputs.length;index++)if(inputs[index]!=null)menu.getSlot(index+1).set(new ItemStack(inputs[index]));
        return (CraftingContainer)menu.getSlot(1).container;
    }
    private static CraftingRecipe recipe(GameTestHelper h,String name){return (CraftingRecipe)h.getLevel().getRecipeManager().byKey(id(name)).orElseThrow();}
    private static void check(GameTestHelper h,String name,Item output,int count,Item...inputs)
    {
        var r=recipe(h,name);yes(h,r.matches(grid(h,inputs),h.getLevel()),"普通合成材料匹配："+name);
        var result=r.getResultItem(h.getLevel().registryAccess());yes(h,result.is(output)&&result.getCount()==count,"普通合成产量："+name);
        var matches=h.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)
                .stream().filter(candidate->candidate.matches(grid(h,inputs),h.getLevel())).toList();
        yes(h,matches.size()==1,"当前已加载配方无冲突："+name+" "+matches.stream().map(Recipe->Recipe.getId().toString()).toList());
    }
    private static void reject(GameTestHelper h,String name,Item...inputs){yes(h,!recipe(h,name).matches(grid(h,inputs),h.getLevel()),"拒绝非法替代："+name);}
}
