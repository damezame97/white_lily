package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.*;
import com.damezame97.white_lily.processing.*;
import com.damezame97.white_lily.item.items;
import com.damezame97.white_lily.block.medical_blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily") @PrefixGameTestTemplate(false) @SuppressWarnings("unused")
public final class RecipeBrowserOptimizationGameTests
{
    @GameTest(template="empty") public static void compactPagesPreserveTargetsAndNeverSplitNormalCards(GameTestHelper h)
    {
        var normal=browser_layout.pages(List.of(100,100,100,100,100),220,5);
        yes(h,normal.size()==3&&normal.get(0).first()==0&&normal.get(0).end()==2&&normal.get(1).first()==2,"multiple complete recipes per page, no partial bottom card");
        var small=browser_layout.pages(List.of(65,65,65,65,65),220,5);
        yes(h,small.size()==2&&small.get(0).end()==3,"smaller recipe units increase actual capacity");
        yes(h,browser_layout.locate(normal,3)==1&&browser_layout.locate(small,3)==1,"stable recipe index is relocated across scale changes");
        var tall=browser_layout.pages(List.of(100,160,60),220,5);
        yes(h,tall.size()==3&&tall.stream().allMatch(p->p.height()<=220),"different device/card heights packed without truncation");
        yes(h,browser_layout.pages(List.of(),10,5).size()==1&&browser_layout.pages(List.of(),10,5).get(0).end()==0,"empty query retains valid 1/1 page");
        yes(h,browser_layout.pages(List.of(500,50),40,5).size()==2,"tiny viewport isolates oversized recipe for scrolling");
        var ids=List.of(id("one"),id("two"),id("three"),id("four"),id("five"));
        var flattened=normal.stream().flatMap(p->ids.subList(p.first(),p.end()).stream()).toList();
        yes(h,flattened.equals(ids),"each actual recipe/plus action has exactly one stable ID, not Ingredient alternatives");h.succeed();
    }
    @GameTest(template="empty") public static void independentScaleGeometryUsesMatchingCoordinates(GameTestHelper h)
    {
        yes(h,browser_layout.cell(50)==9&&browser_layout.cell(100)==18&&browser_layout.cell(150)==27,"item pitch changes with scale");
        int narrow=108;yes(h,narrow/browser_layout.cell(50)>narrow/browser_layout.cell(100),"shrinking cells actually increases columns");
        yes(h,browser_layout.percent(-1)==50&&browser_layout.percent(999)==150,"preference range validated");
        yes(h,browser_layout.fitScale(150,168,76,320,335)==1.5F,"normal viewport respects saved recipe preference");
        float fitted=browser_layout.fitScale(150,168,76,320,127);
        yes(h,Math.round(76*fitted)+38<=127,"small viewport temporarily fits one complete card, without changing saved scale");
        for(float scale:new float[]{.5F,.75F,1F,1.5F}) {
            int ox=7,oy=11,sx=112,sy=30;
            double mx=ox+(sx+8)*scale,my=oy+(sy+8)*scale;
            yes(h,browser_layout.hit(mx,my,ox,oy,scale,sx,sy,18,18),"scaled result hit matches rendered coordinates");
            yes(h,!browser_layout.hit(ox+(sx+18)*scale,my,ox,oy,scale,sx,sy,18,18),"adjacent buttons/slots do not share right boundary");
        }
        h.succeed();
    }
    private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("white_lily",s);}
    private static browser_order.Key key(String name,String namespace,String mod,String path){return new browser_order.Key(name,namespace,mod,namespace+":"+path,"");}
    @GameTest(template="empty") public static void actualChinesePinyinEnglishTiesAndModGroupsAreStable(GameTestHelper h)
    {
        var entries=new ArrayList<>(List.of(key("张", "x","X","z"),key("王","x","X","w"),key("李","x","X","l"),key("陈","x","X","c"),key("阿","x","X","a"),key("赵","x","X","q"),key("八","x","X","b")));
        entries.sort(browser_order.comparator(browser_order.Mode.NAME,"zh_cn"));
        yes(h,entries.stream().map(browser_order.Key::name).toList().equals(List.of("阿","八","陈","李","王","张","赵")),"actual JDK collation uses pinyin rather than Unicode");
        var a=key("§aApple","x","X","b");var b=key("apple","x","X","a");
        yes(h,browser_order.comparator(browser_order.Mode.NAME,"en_us").compare(a,b)>0,"color/case ignored; stable ID breaks ties");
        var variants=List.of(new browser_order.Key("同名","x","X","x:same","{a:2}"),new browser_order.Key("同名","x","X","x:same","{a:1}"));
        yes(h,variants.stream().sorted(browser_order.comparator(browser_order.Mode.NAME,"zh_cn")).findFirst().orElseThrow().variant().equals("{a:1}"),"NBT variants remain separate with a stable tie");
        var groups=new ArrayList<>(List.of(key("A","z","Zoo","a"),key("Z","minecraft","Minecraft","z"),key("B","a","Alpha","b"),key("A","a","Alpha","a"),key("C","z","Zoo","c")));
        groups.sort(browser_order.comparator(browser_order.Mode.MOD,"en_us"));
        yes(h,groups.stream().map(browser_order.Key::namespace).toList().equals(List.of("minecraft","a","a","z","z")),"Minecraft first; namespaces contiguous; internal names sorted");
        yes(h,browser_order.page(99,3,20)==0&&browser_order.page(4,0,1)==0,"filtered/page shrink clamps valid pages");h.succeed();
    }
    @GameTest(template="empty") public static void boundedHistoryCopiesNbtAndRetainsQueryStateWithoutPermissions(GameTestHelper h)
    {
        var history=new browser_history();var stack=new ItemStack(Items.POTION);stack.getOrCreateTag().putString("Potion","minecraft:water");
        var q=new browser_history.Query(stack,false,false,id("category"),id("recipe"),47,"@white_lily",3,true);
        history.push(q,stack,false,false);yes(h,history.isEmpty(),"same target/mode does not add frames");
        history.push(q,stack,true,false);stack.getTag().putString("Potion","minecraft:healing");
        var restored=history.pop();yes(h,!restored.uses()&&restored.scroll()==47&&restored.listPage()==3&&restored.favorites()&&restored.category().equals(id("category")),"mode/page/category/filters restored");
        yes(h,restored.subject().getTag().getString("Potion").equals("minecraft:water"),"query retains an independent NBT variant");
        for(int i=0;i<80;i++)history.push(q,new ItemStack(Items.DIRT),false,false);
        yes(h,history.size()==32,"bounded history holds no Screens or permission snapshots");h.succeed();
    }
    private static processing_block_entity table(GameTestHelper h)
    {h.setBlock(new BlockPos(1,2,1),medical_blocks.SIMPLE_PROCESSING_TABLE.get());return (processing_block_entity)h.getBlockEntity(new BlockPos(1,2,1));}
    @GameTest(template="empty") public static void actualPlannerReportsMissingToolMaterialAndFullRecoveryWithoutMutation(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h)){
            var p=ProcessingTestPlayers.create(h);var t=table(h);p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);
            var menu=new processing_menu(72,p.getInventory(),t);p.containerMenu=menu;
            var recipe=(processing_recipe)h.getLevel().getRecipeManager().byKey(id("processing/hemp_fiber")).orElseThrow();
            p.getInventory().clearContent();
            yes(h,menu.previewRecipe(p,recipe.getId())==transfer_reason.MISSING_TOOL,"specific missing tool");
            p.getInventory().setItem(0,new ItemStack(items.STONE_MORTAR.get()));
            yes(h,menu.previewRecipe(p,recipe.getId())==transfer_reason.MISSING_MATERIALS,"specific missing raw material");
            p.getInventory().setItem(1,new ItemStack(items.HEMP_STALK.get(),3));var saved=t.saveWithFullMetadata();
            yes(h,menu.previewRecipe(p,recipe.getId())==transfer_reason.OK&&t.saveWithFullMetadata().equals(saved),"preview succeeds without mutation");
            yes(h,browser_transfer.apply(p,72,recipe.getId(),"outdated",false)==transfer_reason.DATA_CHANGED,"fingerprint rejects stale data before debit");
            yes(h,p.getInventory().getItem(1).getCount()==3,"stale request stock unchanged");
            yes(h,recipe_unlocks.processTransfer(p,72,recipe.getId(),false,browser_transfer.fingerprint(recipe),7)==transfer_reason.OK,"real server transfer succeeds");
            var after=t.saveWithFullMetadata();int held=p.getInventory().countItem(items.HEMP_STALK.get());
            yes(h,recipe_unlocks.processTransfer(p,72,recipe.getId(),false,browser_transfer.fingerprint(recipe),7)==transfer_reason.OK,"duplicate request reuses result without executing again");
            yes(h,browser_transfer.apply(p,72,recipe.getId(),browser_transfer.fingerprint(recipe),false)==transfer_reason.COOLDOWN,"repeat cannot refill immediately");
            yes(h,t.saveWithFullMetadata().equals(after)&&held==p.getInventory().countItem(items.HEMP_STALK.get()),"repeat failure atomic");
            for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
            yes(h,menu.previewRecipe(p,recipe.getId())==transfer_reason.INVENTORY_FULL,"recovery capacity is distinct");
            p.setPos(t.getBlockPos().getX()+100,t.getBlockPos().getY(),t.getBlockPos().getZ());
            yes(h,browser_transfer.apply(p,72,recipe.getId(),browser_transfer.fingerprint(recipe),false)==transfer_reason.MENU_INVALID,"distance invalid");
        }h.succeed();
    }
    @GameTest(template="empty") public static void containerAndSlotAllocationFailuresComeFromTheActualPlanner(GameTestHelper h)
    {
        var manager=h.getLevel().getRecipeManager();var originals=new ArrayList<Recipe<?>>(manager.getRecipes());
        try(var scope=new ProcessingTestPlayers(h)){
            var p=ProcessingTestPlayers.create(h);var t=table(h);p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);var menu=new processing_menu(74,p.getInventory(),t);p.containerMenu=menu;
            var json=com.google.gson.JsonParser.parseString("{\"tool\":{\"item\":\"white_lily:stone_mortar\"},\"ingredients\":[{\"ingredient\":{\"item\":\"minecraft:stick\"}}],\"container\":{\"ingredient\":{\"item\":\"minecraft:bowl\"}},\"result\":{\"item\":\"minecraft:dirt\"},\"processing_time\":40}").getAsJsonObject();
            var fixture=new processing_recipe.Serializer().fromJson(id("container_fixture"),json);var all=new ArrayList<Recipe<?>>(originals);all.add(fixture);manager.replaceRecipes(all);
            p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(items.STONE_MORTAR.get()));p.getInventory().setItem(1,new ItemStack(Items.STICK));
            yes(h,menu.previewRecipe(p,fixture.getId())==transfer_reason.MISSING_CONTAINER,"container shortage distinct from raw shortage");
            p.getInventory().setItem(2,new ItemStack(Items.BOWL));yes(h,menu.previewRecipe(p,fixture.getId())==transfer_reason.OK,"real container resolves shortage");
            json.remove("container");var ingredients=new com.google.gson.JsonArray();
            int slot=1;for(Item item:List.of(Items.STONE,Items.DIRT,Items.CLAY_BALL,Items.STICK))
            {var material=new com.google.gson.JsonObject();var ingredient=new com.google.gson.JsonObject();ingredient.addProperty("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());material.add("ingredient",ingredient);ingredients.add(material);p.getInventory().setItem(slot++,new ItemStack(item));}
            json.add("ingredients",ingredients);var four=new processing_recipe.Serializer().fromJson(id("allocation_fixture"),json);all.add(four);manager.replaceRecipes(all);
            yes(h,menu.previewRecipe(p,four.getId())==transfer_reason.ALLOCATION,"four required NBT/item groups cannot fit three raw slots");
        }finally{manager.replaceRecipes(originals);}h.succeed();
    }
    @GameTest(template="empty") public static void restrictedAndWrongDevicesNeverGainRightsThroughTransfer(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h)){
            var p=ProcessingTestPlayers.create(h);var t=table(h);p.setPos(t.getBlockPos().getX()+.5,t.getBlockPos().getY(),t.getBlockPos().getZ()+.5);var menu=new processing_menu(73,p.getInventory(),t);p.containerMenu=menu;
            var recipe=(processing_recipe)h.getLevel().getRecipeManager().byKey(id("processing/bitterleaf_paste")).orElseThrow();
            yes(h,menu.previewRecipe(p,recipe.getId())==transfer_reason.UNREGISTERED,"unregistered even with a valid recipe ID");
            p.containerMenu=p.inventoryMenu;yes(h,browser_transfer.preview(p,recipe)==transfer_reason.WRONG_DEVICE,"processing cannot fill inventory crafting");
            var tea=h.getLevel().getRecipeManager().byKey(id("calming_flower_tea")).orElseThrow();
            yes(h,browser_transfer.preview(p,tea)==transfer_reason.NOT_AUTHORIZED,"personal research not bypassed");
            yes(h,browser_transfer.apply(p,999,recipe.getId(),browser_transfer.fingerprint(recipe),false)==transfer_reason.MENU_INVALID,"wrong menu cannot recover machine context");
        }h.succeed();
    }
    @GameTest(template="empty") public static void deviceDisplayUsesActualShapeCookingAndProcessingFields(GameTestHelper h)
    {
        var manager=h.getLevel().getRecipeManager();var access=h.getLevel().registryAccess();
        var json=com.google.gson.JsonParser.parseString("{\"pattern\":[\"X X\",\" X \"],\"key\":{\"X\":{\"item\":\"minecraft:stone\"}},\"result\":{\"item\":\"minecraft:stone\"}}").getAsJsonObject();
        var shaped=RecipeSerializer.SHAPED_RECIPE.fromJson(id("display_fixture"),json);var display=browser_recipe_display.build(shaped,access);
        yes(h,display.slots().stream().filter(s->!s.output()).map(s->s.x()+","+s.y()).toList().equals(List.of("4,10","44,10","24,30")),"empty shaped cells are preserved in the 3x3 diagram");
        // Do not select an arbitrary mod recipe: some legitimately produce coal as their result.
        var cooking=manager.byKey(ResourceLocation.fromNamespaceAndPath("minecraft","charcoal")).orElseThrow();var cooked=browser_recipe_display.build(cooking,access);
        yes(h,cooked.slots().stream().noneMatch(s->s.ingredient().test(new ItemStack(Items.COAL)))&&cooked.slots().stream().anyMatch(s->s.output()&&s.x()==116),"fuel is a marker, not a fake coal ingredient");
        var recipe=(processing_recipe)manager.byKey(id("processing/bitterleaf_paste")).orElseThrow();var processing=browser_recipe_display.build(recipe,access);
        yes(h,processing.outputs().stream().anyMatch(stack->stack.is(items.BITTERLEAF_PASTE.get())&&stack.getCount()==2)&&processing.outputs().stream().anyMatch(stack->stack.is(items.HERBAL_RESIDUE.get())&&stack.getCount()==1),"actual output and byproduct counts");
        yes(h,processing.slots().stream().anyMatch(s->s.x()==4&&s.y()==36&&s.ingredient().test(new ItemStack(items.STONE_MORTAR.get()))),"tool distinguished from raw ingredients");
        var honey=browser_recipe_display.build(manager.byKey(id("clean_fine_bandage_honey")).orElseThrow(),access);
        yes(h,honey.slots().stream().anyMatch(s->s.ingredient().test(new ItemStack(Items.GLASS_BOTTLE))),"actual honey container return shown");
        var revealing=browser_recipe_display.build(manager.byKey(id("revealed_prescription")).orElseThrow(),access);
        yes(h,revealing.slots().stream().anyMatch(s->s.output()&&s.ingredient().test(new ItemStack(Items.GLASS_BOTTLE))),"custom water-bottle remainder shown even with NBT ingredient");
        var craft=manager.byKey(ResourceLocation.fromNamespaceAndPath("minecraft","crafting_table")).orElseThrow();
        var alternatives=browser_recipe_display.build(craft,access);
        yes(h,alternatives.slots().stream().filter(s->!s.output()).count()==4&&alternatives.slots().stream().anyMatch(s->s.ingredient().getItems().length>1),"plank alternatives remain one recipe with four input slots, not duplicated recipes");h.succeed();
    }
}
