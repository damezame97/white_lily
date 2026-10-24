package com.damezame97.white_lily.recipebook;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.*;
import java.util.function.Consumer;

/** Ordered, client presentation rules. Never queries or changes recipe permissions. */
public final class browser_categories {
    public record Category(String id,String translation) { }
    private record Rule(Category category,List<ResourceLocation> items,List<TagKey<Item>> tags,List<String> kinds) {
        boolean matches(ItemStack stack){return items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))||tags.stream().anyMatch(stack::is)||kinds.stream().anyMatch(kind->kind(kind,stack));}
    }
    public static final class Rules {
        private final List<Rule> rules;
        private final Map<Item,Optional<Category>> cache=new IdentityHashMap<>();
        private Rules(List<Rule> rules){this.rules=List.copyOf(rules);}
        public Category classify(ItemStack stack){return cache.computeIfAbsent(stack.getItem(),item->rules.stream().filter(r->r.matches(stack)).map(Rule::category).findFirst()).orElse(null);}
        public void invalidate(){cache.clear();}
        public int size(){return rules.size();}
    }
    public static Rules empty(){return new Rules(List.of());}
    /** Bad individual rules are skipped with file/rule location, never interpreted as catch-all rules. */
    public static Rules parse(JsonElement json,Consumer<String> warning){
        List<Rule> rules=new ArrayList<>();
        if(!json.isJsonObject()||!json.getAsJsonObject().has("rules")||!json.getAsJsonObject().get("rules").isJsonArray()){warning.accept("rules: expected an array");return empty();}
        int index=0;for(JsonElement value:json.getAsJsonObject().getAsJsonArray("rules")){
            int at=index++;
            try{JsonObject entry=value.getAsJsonObject();String id=entry.get("category").getAsString();if(!id.matches("[a-z0-9_]+"))throw new IllegalArgumentException("invalid category ID");
                Category category=new Category(id,"screen.white_lily.recipe_browser.sort_category."+id);
                List<ResourceLocation> ids=references(entry,"items");List<TagKey<Item>> tags=references(entry,"tags").stream().map(idTag->TagKey.create(Registries.ITEM,idTag)).toList();
                for(var itemId:ids)if(!BuiltInRegistries.ITEM.containsKey(itemId))warning.accept("rules["+at+"]: item not registered: "+itemId+" (selector cannot match)");
                List<String> kinds=strings(entry,"kinds");for(String kind:kinds)if(!KINDS.contains(kind))throw new IllegalArgumentException("unknown kind "+kind);
                if(ids.isEmpty()&&tags.isEmpty()&&kinds.isEmpty())throw new IllegalArgumentException("no match selectors");
                rules.add(new Rule(category,ids,tags,kinds));
            }catch(RuntimeException e){warning.accept("rules["+at+"]: "+e.getMessage());}
        }return new Rules(rules);
    }
    private static List<String> strings(JsonObject object,String key){if(!object.has(key))return List.of();List<String> result=new ArrayList<>();for(JsonElement value:object.getAsJsonArray(key))result.add(value.getAsString());return List.copyOf(result);}
    private static List<ResourceLocation> references(JsonObject object,String key){return strings(object,key).stream().map(text->{var id=ResourceLocation.tryParse(text);if(id==null)throw new IllegalArgumentException("invalid "+key+" ID "+text);return id;}).toList();}
    private static final Set<String> KINDS=Set.of("slab","stairs","door","trapdoor","button","pressure_plate","fence","fence_gate","armor","weapon","tool","food");
    private static boolean kind(String kind,ItemStack stack){
        Item item=stack.getItem();Block block=item instanceof BlockItem bi?bi.getBlock():Blocks.AIR;
        return switch(kind){
            case "slab"->block instanceof SlabBlock;case "stairs"->block instanceof StairBlock;case "door"->block instanceof DoorBlock;
            case "trapdoor"->block instanceof TrapDoorBlock;case "button"->block instanceof ButtonBlock;case "pressure_plate"->block instanceof BasePressurePlateBlock;
            case "fence"->block instanceof FenceBlock;case "fence_gate"->block instanceof FenceGateBlock;
            case "armor"->item instanceof ArmorItem;case "weapon"->item instanceof SwordItem||item instanceof ProjectileWeaponItem||item instanceof TridentItem;
            case "tool"->item instanceof DiggerItem||item instanceof ShearsItem||item instanceof FishingRodItem||item instanceof FlintAndSteelItem;
            case "food"->stack.isEdible();default->false;};
    }
    private browser_categories() { }
}
