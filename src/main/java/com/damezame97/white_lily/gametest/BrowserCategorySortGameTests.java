package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.*;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

@GameTestHolder("white_lily") @PrefixGameTestTemplate(false)
public final class BrowserCategorySortGameTests {
    private static browser_categories.Rules bundled(){
        try(var stream=BrowserCategorySortGameTests.class.getClassLoader().getResourceAsStream("assets/white_lily/recipe_browser/categories.json")){
            return browser_categories.parse(JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)),message->{throw new AssertionError(message);});
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static browser_order.Key key(String name,String namespace,String path,String group){return new browser_order.Key(name,namespace,namespace,namespace+":"+path,"",group,group);}
    @GameTest(template="empty") public static void actualTagsTypesAndExplicitRulesHaveFixedPriority(GameTestHelper h){
        var rules=bundled();
        for(Item item:List.of(Items.OAK_LOG,Items.STRIPPED_OAK_LOG,Items.CRIMSON_STEM,Items.STRIPPED_WARPED_STEM))yes(h,rules.classify(new ItemStack(item)).id().equals("logs"),"normal/stripped logs and fungal stems share logs");
        yes(h,rules.classify(new ItemStack(Items.OAK_PLANKS)).id().equals("planks"),"planks are separate from logs");
        yes(h,rules.classify(new ItemStack(Items.OAK_STAIRS)).id().equals("stairs")&&rules.classify(new ItemStack(Items.STONE_SLAB)).id().equals("slabs"),"block shapes classified without name substrings");
        yes(h,rules.classify(new ItemStack(Items.IRON_INGOT)).id().equals("ingots")&&rules.classify(new ItemStack(Items.IRON_NUGGET)).id().equals("nuggets"),"actual Forge metal tags");
        yes(h,rules.classify(new ItemStack(Items.IRON_SWORD)).id().equals("weapons")&&rules.classify(new ItemStack(Items.IRON_PICKAXE)).id().equals("tools"),"specific weapons before broad tool tag");
        yes(h,rules.classify(new ItemStack(Items.APPLE)).id().equals("food"),"food property fallback");
        for(String itemId:List.of("white_lily:bitterleaf","white_lily:calming_flower_tea","white_lily:stone_mortar","white_lily:plant_fiber","tacz:modern_kinetic_gun")){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(itemId));
            yes(h,item!=Items.AIR&&rules.classify(new ItemStack(item))!=null,"actual project items and gun carrier explicitly classified: "+itemId);
        }
        var partial=com.google.gson.JsonParser.parseString("{\"rules\":[{\"category\":\"first\",\"tags\":[\"minecraft:logs\"]},{\"category\":\"later\",\"items\":[\"minecraft:oak_log\"]}]}");
        var first=browser_categories.parse(partial,message->{throw new AssertionError(message);});
        yes(h,first.classify(new ItemStack(Items.OAK_LOG)).id().equals("first"),"first matching rule wins, not first item tag");
        var reversed=partial.getAsJsonObject().getAsJsonArray("rules");var old=reversed.remove(0);reversed.add(old);var reloaded=browser_categories.parse(partial,message->{throw new AssertionError(message);});
        yes(h,reloaded.classify(new ItemStack(Items.OAK_LOG)).id().equals("later"),"new rules do not reuse stale classification cache");first.invalidate();
        h.succeed();
    }
    @GameTest(template="empty") public static void threeModesGroupAcrossNamespacesOrIgnoreGroupsAsSpecified(GameTestHelper h){
        var entries=List.of(key("Z log","minecraft","log","Logs"),key("A plank","minecraft","plank","Planks"),key("A log","other","log","Logs"),key("Z plank","other","plank","Planks"));
        var global=entries.stream().sorted(browser_order.comparator(browser_order.Mode.DEFAULT,"en_us")).toList();
        yes(h,global.stream().map(browser_order.Key::categoryId).toList().equals(List.of("Logs","Logs","Planks","Planks")),"global groups mix namespaces");
        yes(h,global.get(0).namespace().equals("other"),"names sort within global category, not by mod");
        var modules=entries.stream().sorted(browser_order.comparator(browser_order.Mode.MOD,"en_us")).toList();
        yes(h,modules.stream().map(browser_order.Key::namespace).toList().equals(List.of("minecraft","minecraft","other","other"))&&modules.stream().map(browser_order.Key::categoryId).toList().equals(List.of("Logs","Planks","Logs","Planks")),"module first, then category, then names");
        var names=entries.stream().sorted(browser_order.comparator(browser_order.Mode.NAME,"en_us")).toList();
        yes(h,names.stream().map(browser_order.Key::name).toList().equals(List.of("A log","A plank","Z log","Z plank")),"name mode completely ignores categories");
        var unknown=new ArrayList<>(entries);unknown.add(new browser_order.Key("Apple","u","u","u:a",""));unknown.add(new browser_order.Key("Zebra","u","u","u:z",""));unknown.sort(browser_order.comparator(browser_order.Mode.DEFAULT,"en_us"));
        yes(h,unknown.get(0).name().equals("Apple")&&unknown.get(5).name().equals("Zebra"),"unknowns use own names, not a giant Other group");
        var chinese=List.of(key("张木","b","z","原木"),key("阿木","a","a","原木"),key("八板","b","b","木板"));
        yes(h,chinese.stream().sorted(browser_order.comparator(browser_order.Mode.DEFAULT,"zh_cn")).map(browser_order.Key::name).toList().equals(List.of("八板","阿木","张木")),"category Mu before Yuan and A before Zhang use actual pinyin");
        h.succeed();
    }
    @GameTest(template="empty") public static void filteredScaledGridAddsOnlyModuleHeadersAndNoCategorySpaces(GameTestHelper h){
        var entries=List.of(key("A","minecraft","a","Logs"),key("B","minecraft","b","Planks"),key("C","mod","c","Logs"),key("D","mod","d","Planks"));
        for(int size:List.of(9,18,27))for(int cols:List.of(1,3,8)){
            var plain=browser_order.grid(entries,cols,4,size,false,browser_order.Key::namespace,browser_order.Key::modName);
            yes(h,plain.stream().flatMap(Collection::stream).noneMatch(c->c.item()==null),"default/name never insert category or mod placeholders");
            for(var page:plain)for(int i=0;i<page.size();i++)yes(h,page.get(i).position()==i,"plain grid densely packed across category boundaries");
            var filtered=entries.stream().filter(k->k.namespace().equals("mod")).toList();
            var grouped=browser_order.grid(filtered,cols,4,size,true,browser_order.Key::namespace,browser_order.Key::modName);
            yes(h,grouped.stream().flatMap(Collection::stream).filter(c->c.item()!=null).map(browser_order.Cell::item).toList().equals(filtered),"search/favorite subset neither loses nor adds unauthorized entries");
            for(var page:grouped){yes(h,page.stream().allMatch(c->c.position()<cols*4),"headers participate in real page capacity");yes(h,page.stream().anyMatch(c->c.item()!=null),"no empty module-only pages");yes(h,page.stream().filter(c->c.item()==null).allMatch(c->c.header().equals("mod")),"no empty filtered module headings");}
        }
        yes(h,browser_order.grid(List.<browser_order.Key>of(),1,1,9,true,browser_order.Key::namespace,browser_order.Key::modName).size()==1,"empty filter still has a valid page");
        h.succeed();
    }
    @GameTest(template="empty") public static void invalidRulesAreIsolatedAndFallbackIsSafe(GameTestHelper h){
        var warnings=new ArrayList<String>();var rules=browser_categories.parse(JsonParser.parseString("{\"rules\":[{\"category\":\"bad\",\"kinds\":[\"unknown\"]},{\"category\":\"empty\"},{\"category\":\"good\",\"items\":[\"minecraft:stick\"]}]}"),warnings::add);
        yes(h,rules.size()==1&&warnings.size()==2&&warnings.get(0).contains("rules[0]")&&warnings.get(1).contains("rules[1]"),"invalid selectors have locatable warnings and do not become catch-all");
        yes(h,rules.classify(new ItemStack(Items.STICK)).id().equals("good")&&rules.classify(new ItemStack(Items.DIRT))==null,"bad configuration keeps name fallback and valid rules");
        h.succeed();
    }
}
