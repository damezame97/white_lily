package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.recipe_unlock_rules;
import com.damezame97.white_lily.recipebook.recipe_unlocks;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.*;

import java.util.Set;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Data-driven survival routes: actual tags, recipe types, input families and AND conditions. */
@GameTestHolder("white_lily") @PrefixGameTestTemplate(false) @SuppressWarnings("unused")
public final class RecipeUnlockRoutesGameTests
{
    private static final ResourceLocation OAK_PLANKS=id("oak_planks"), SPRUCE_PLANKS=id("spruce_planks");
    private static final ResourceLocation BAMBOO_PLANKS=id("bamboo_planks"), CRIMSON_PLANKS=id("crimson_planks");
    private static final ResourceLocation OAK_STAIRS=id("oak_stairs"), OAK_SIGN=id("oak_sign");
    private static final ResourceLocation WOODEN_PICKAXE=id("wooden_pickaxe");
    private static final ResourceLocation CHEST=id("chest"), LADDER=id("ladder");
    private static final ResourceLocation OAK_BED=id("oak_bed"), OAK_BOAT=id("oak_boat");
    private static final ResourceLocation CHARCOAL=id("charcoal"), TORCH=id("torch");
    private static final ResourceLocation STONE_PICKAXE=id("stone_pickaxe"), FURNACE=id("furnace"), LEVER=id("lever");
    private static final ResourceLocation BLAST_FURNACE=id("blast_furnace");
    private static final ResourceLocation COBBLESTONE_STAIRS=id("cobblestone_stairs");
    private static final ResourceLocation BLACKSTONE_STAIRS=id("blackstone_stairs");
    private static final ResourceLocation DEEPSLATE_STAIRS=id("cobbled_deepslate_stairs");
    private static final ResourceLocation STONE_BRICKS=id("stone_bricks");
    private static final ResourceLocation STONE_BRICK_STAIRS=id("stone_brick_stairs");
    private static final ResourceLocation SMOOTH_STONE_SLAB=id("smooth_stone_slab");
    private static final ResourceLocation POLISHED_DEEPSLATE=id("polished_deepslate");
    private static final ResourceLocation DEEPSLATE_BRICKS=id("deepslate_bricks");
    private static final ResourceLocation DEEPSLATE_TILES=id("deepslate_tiles");
    private static final ResourceLocation IRON_SMELT_RAW=id("iron_ingot_from_smelting_raw_iron");
    private static final ResourceLocation IRON_SMELT_ORE=id("iron_ingot_from_smelting_iron_ore");
    private static final ResourceLocation IRON_BLAST_RAW=id("iron_ingot_from_blasting_raw_iron");
    private static final ResourceLocation IRON_BLAST_ORE=id("iron_ingot_from_blasting_iron_ore");
    private static final ResourceLocation IRON_BLOCK=id("iron_block"), IRON_NUGGET=id("iron_nugget");

    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("minecraft",path);}
    private static void acquire(ServerPlayer player,Item item){recipe_unlocks.acquired(player,new ItemStack(item));}
    private static boolean unlocked(ServerPlayer player,ResourceLocation recipe){return recipe_unlocks.isUnlocked(player,recipe);}
    private static boolean exists(GameTestHelper h,ResourceLocation recipe){return h.getLevel().getRecipeManager().byKey(recipe).isPresent();}

    @GameTest(template="empty") public static void tagsActuallyCoverStrippedWoodBambooAndConstructionOutputs(GameTestHelper h)
    {
        yes(h,new ItemStack(Items.STRIPPED_OAK_LOG).is(ItemTags.LOGS),"logs tag contains stripped logs");
        yes(h,new ItemStack(Items.BAMBOO_BLOCK).is(ItemTags.BAMBOO_BLOCKS),"bamboo block tag is real");
        yes(h,new ItemStack(Items.BAMBOO_PLANKS).is(ItemTags.PLANKS),"planks tag contains bamboo");
        yes(h,new ItemStack(Items.OAK_SIGN).is(ItemTags.SIGNS),"signs tag contains regular signs");
        yes(h,new ItemStack(Items.OAK_STAIRS).is(ItemTags.WOODEN_STAIRS),"wooden stairs tag is real");
        yes(h,new ItemStack(Items.OAK_DOOR).is(ItemTags.WOODEN_DOORS)&&new ItemStack(Items.OAK_TRAPDOOR).is(ItemTags.WOODEN_TRAPDOORS),
                "wooden doors and trapdoors are tagged");
        yes(h,new ItemStack(Items.OAK_FENCE).is(ItemTags.WOODEN_FENCES)&&new ItemStack(Items.OAK_FENCE_GATE).is(ItemTags.FENCE_GATES),
                "wooden fences and fence gates are tagged");
        yes(h,new ItemStack(Items.COBBLESTONE).is(ItemTags.STONE_TOOL_MATERIALS)&&new ItemStack(Items.BLACKSTONE).is(ItemTags.STONE_TOOL_MATERIALS)
                &&new ItemStack(Items.COBBLED_DEEPSLATE).is(ItemTags.STONE_TOOL_MATERIALS),"stone tool materials cover all three basic stones");
        yes(h,new ItemStack(Items.COAL).is(ItemTags.COALS)&&new ItemStack(Items.CHARCOAL).is(ItemTags.COALS),"coals tag contains coal and charcoal");
        h.succeed();
    }

    @GameTest(template="empty") public static void noRouteIsOpenBeforeDiscovery(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var p=ProcessingTestPlayers.create(h);
            yes(h,recipe_unlock_rules.initial().stream().allMatch(g->g.recipes().isEmpty()&&g.outputs().isEmpty()),"initial route list stays empty");
            yes(h,!unlocked(p,OAK_PLANKS)&&!unlocked(p,STONE_PICKAXE)&&!unlocked(p,TORCH),"new player has no free wood, stone or torch route");
        }h.succeed();
    }

    @GameTest(template="empty") public static void oneLogOrPlankOpensEveryWoodSpeciesConstructionRecipe(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var viaLog=ProcessingTestPlayers.create(h);acquire(viaLog,Items.OAK_LOG);
            yes(h,unlocked(viaLog,OAK_PLANKS)&&unlocked(viaLog,SPRUCE_PLANKS)&&unlocked(viaLog,BAMBOO_PLANKS)&&unlocked(viaLog,CRIMSON_PLANKS),
                    "one log opens planks for every wood family, not just oak");
            yes(h,unlocked(viaLog,OAK_STAIRS)&&unlocked(viaLog,OAK_SIGN)&&unlocked(viaLog,WOODEN_PICKAXE)&&unlocked(viaLog,CHEST)&&unlocked(viaLog,LADDER),
                    "log opens the confirmed wooden building/tool set");
            yes(h,!unlocked(viaLog,OAK_BED)&&!unlocked(viaLog,OAK_BOAT)&&!unlocked(viaLog,CHARCOAL)&&!unlocked(viaLog,TORCH),
                    "wood discovery does not open beds, boats, charcoal or torches");
            var viaStripped=ProcessingTestPlayers.create(h);acquire(viaStripped,Items.STRIPPED_OAK_LOG);
            yes(h,unlocked(viaStripped,SPRUCE_PLANKS),"stripped log is a valid wood-family trigger");
            var viaPlanks=ProcessingTestPlayers.create(h);acquire(viaPlanks,Items.OAK_PLANKS);
            yes(h,unlocked(viaPlanks,BAMBOO_PLANKS)&&unlocked(viaPlanks,CRIMSON_PLANKS),"planks trigger the whole wood route");
            var viaBamboo=ProcessingTestPlayers.create(h);acquire(viaBamboo,Items.BAMBOO_BLOCK);
            yes(h,unlocked(viaBamboo,BAMBOO_PLANKS),"bamboo blocks trigger the bamboo construction route");
        }h.succeed();
    }

    @GameTest(template="empty") public static void basicStoneIsSharedButBuildingMaterialsStaySeparate(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var cobble=ProcessingTestPlayers.create(h);acquire(cobble,Items.COBBLESTONE);
            yes(h,unlocked(cobble,STONE_PICKAXE)&&unlocked(cobble,FURNACE)&&unlocked(cobble,LEVER),"cobblestone opens the shared stone tools, furnace and lever");
            yes(h,unlocked(cobble,COBBLESTONE_STAIRS)&&!unlocked(cobble,BLACKSTONE_STAIRS)&&!unlocked(cobble,DEEPSLATE_STAIRS),
                    "cobblestone building does not open blackstone or deepslate buildings");
            var blackstone=ProcessingTestPlayers.create(h);acquire(blackstone,Items.BLACKSTONE);
            yes(h,unlocked(blackstone,STONE_PICKAXE)&&unlocked(blackstone,BLACKSTONE_STAIRS)&&!unlocked(blackstone,COBBLESTONE_STAIRS),
                    "blackstone opens basic stone capability and only blackstone buildings");
            var deepslate=ProcessingTestPlayers.create(h);acquire(deepslate,Items.COBBLED_DEEPSLATE);
            yes(h,unlocked(deepslate,STONE_PICKAXE)&&unlocked(deepslate,DEEPSLATE_STAIRS)&&!unlocked(deepslate,COBBLESTONE_STAIRS),
                    "cobbled deepslate opens its own building family");
        }h.succeed();
    }

    @GameTest(template="empty") public static void stoneBuildingChainsFollowDiscoveredInputFamily(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var stone=ProcessingTestPlayers.create(h);acquire(stone,Items.STONE);
            yes(h,unlocked(stone,STONE_BRICKS)&&unlocked(stone,id("stone_slab"))&&unlocked(stone,id("stone_pressure_plate"))&&!unlocked(stone,STONE_BRICK_STAIRS),
                    "stone opens its own slabs/pressure plate and the stone-brick intermediate; brick stairs wait for bricks");
            acquire(stone,Items.STONE_BRICKS);
            yes(h,unlocked(stone,STONE_BRICK_STAIRS),"discovering stone bricks opens stone-brick building recipes");
            var smooth=ProcessingTestPlayers.create(h);acquire(smooth,Items.SMOOTH_STONE);
            boolean smoothStairs=exists(h,id("smooth_stone_stairs"));
            yes(h,unlocked(smooth,SMOOTH_STONE_SLAB)&&(!smoothStairs||unlocked(smooth,id("smooth_stone_stairs")))&&!unlocked(smooth,id("smooth_stone_wall")),
                    "smooth stone opens only the recipes that really exist");
            var chain=ProcessingTestPlayers.create(h);acquire(chain,Items.COBBLED_DEEPSLATE);
            yes(h,unlocked(chain,POLISHED_DEEPSLATE)&&!unlocked(chain,DEEPSLATE_BRICKS),"cobbled deepslate unlocks only its next intermediate");
            acquire(chain,Items.POLISHED_DEEPSLATE);
            yes(h,unlocked(chain,DEEPSLATE_BRICKS)&&!unlocked(chain,DEEPSLATE_TILES),"polished deepslate unlocks deepslate bricks next");
            acquire(chain,Items.DEEPSLATE_BRICKS);
            yes(h,unlocked(chain,DEEPSLATE_TILES),"deepslate bricks unlock deepslate tiles next");
            var irrelevant=ProcessingTestPlayers.create(h);acquire(irrelevant,Items.COBBLESTONE);
            yes(h,!unlocked(irrelevant,id("quartz_stairs"))&&!unlocked(irrelevant,id("end_stone_brick_stairs"))&&!unlocked(irrelevant,id("prismarine_stairs")),
                    "one stone family never opens unrelated stone families");
        }h.succeed();
    }

    @GameTest(template="empty") public static void smeltingOnlyOpensTheCorrespondingRealRecipe(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var iron=ProcessingTestPlayers.create(h);acquire(iron,Items.RAW_IRON);
            yes(h,unlocked(iron,IRON_SMELT_RAW)&&!unlocked(iron,IRON_SMELT_ORE)&&!unlocked(iron,IRON_BLOCK)&&!unlocked(iron,IRON_NUGGET),
                    "raw iron opens only its own smelting recipe, not ore/block/nugget routes");
            yes(h,!unlocked(iron,BLAST_FURNACE)&&!unlocked(iron,IRON_BLAST_RAW)&&!unlocked(iron,id("iron_pickaxe")),"raw iron alone does not open the blast furnace or iron equipment");
            var gold=ProcessingTestPlayers.create(h);acquire(gold,Items.RAW_GOLD);
            var copper=ProcessingTestPlayers.create(h);acquire(copper,Items.RAW_COPPER);
            yes(h,unlocked(gold,id("gold_ingot_from_smelting_raw_gold"))&&!unlocked(gold,id("gold_block"))&&!unlocked(gold,id("golden_pickaxe")),
                    "raw gold opens only its own smelt, not gold equipment or storage conversion");
            yes(h,unlocked(copper,id("copper_ingot_from_smelting_raw_copper"))&&!unlocked(copper,id("copper_block")),
                    "raw copper opens only its own smelt, not copper building routes");
            var sand=ProcessingTestPlayers.create(h);acquire(sand,Items.SAND);
            yes(h,unlocked(sand,id("glass"))&&!unlocked(sand,id("glass_pane")),"sand opens glass smelting, not glass products");
            var clay=ProcessingTestPlayers.create(h);acquire(clay,Items.CLAY_BALL);
            acquire(clay,Items.CLAY);
            yes(h,unlocked(clay,id("brick"))&&unlocked(clay,id("terracotta")),"clay ball/block open their real brick/terracotta smelts");
            var stone=ProcessingTestPlayers.create(h);acquire(stone,Items.COBBLESTONE);
            yes(h,unlocked(stone,id("stone"))&&!unlocked(stone,id("smooth_stone")),"cobblestone smelts to stone but does not skip to smooth stone");
            acquire(stone,Items.STONE);
            yes(h,unlocked(stone,id("smooth_stone")),"stone opens the real stone-to-smooth-stone smelt");
            var deep=ProcessingTestPlayers.create(h);acquire(deep,Items.COBBLED_DEEPSLATE);
            yes(h,unlocked(deep,id("deepslate")),"cobbled deepslate opens the real deepslate smelt");
            var food=ProcessingTestPlayers.create(h);acquire(food,Items.BEEF);
            yes(h,unlocked(food,id("cooked_beef"))&&!unlocked(food,id("cooked_porkchop")),"one food opens only its own smelting/smoking recipes");
        }h.succeed();
    }

    @GameTest(template="empty") public static void furnaceCoalAndCharcoalRoutesUseActualRecipes(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var furnace=ProcessingTestPlayers.create(h);acquire(furnace,Items.FURNACE);
            yes(h,unlocked(furnace,id("smoker")),"obtaining a furnace unlocks the smoker recipe");
            boolean charcoal=false;
            for(Recipe<?> recipe:h.getLevel().getRecipeManager().getRecipes())
                if(recipe.getResultItem(h.getLevel().registryAccess()).is(Items.CHARCOAL)&&unlocked(furnace,recipe.getId()))charcoal=true;
            yes(h,charcoal,"obtaining a furnace unlocks at least one real charcoal smelting recipe");
            var coal=ProcessingTestPlayers.create(h);acquire(coal,Items.COAL);
            yes(h,unlocked(coal,TORCH),"coal alone opens the torch recipe");
            var charcoalItem=ProcessingTestPlayers.create(h);acquire(charcoalItem,Items.CHARCOAL);
            yes(h,unlocked(charcoalItem,TORCH),"charcoal alone opens the torch recipe");
        }h.succeed();
    }

    @GameTest(template="empty") public static void blastFurnaceRecipeAndSmeltingAreOrderIndependent(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var ironFirst=ProcessingTestPlayers.create(h);acquire(ironFirst,Items.IRON_INGOT);acquire(ironFirst,Items.COBBLESTONE);
            yes(h,unlocked(ironFirst,FURNACE)&&unlocked(ironFirst,BLAST_FURNACE),"iron ingot before furnace still unlocks the blast furnace");
            var furnaceFirst=ProcessingTestPlayers.create(h);acquire(furnaceFirst,Items.COBBLESTONE);acquire(furnaceFirst,Items.IRON_INGOT);
            yes(h,unlocked(furnaceFirst,BLAST_FURNACE),"furnace-recipe unlock before iron still unlocks the blast furnace");
            var oreFirst=ProcessingTestPlayers.create(h);acquire(oreFirst,Items.RAW_IRON);acquire(oreFirst,Items.COBBLESTONE);acquire(oreFirst,Items.IRON_INGOT);
            yes(h,unlocked(oreFirst,BLAST_FURNACE)&&unlocked(oreFirst,IRON_BLAST_RAW)&&!unlocked(oreFirst,IRON_BLAST_ORE),
                    "raw iron seen before blast furnace is backfilled after both conditions, without opening iron ore");
            var blastFirst=ProcessingTestPlayers.create(h);acquire(blastFirst,Items.COBBLESTONE);acquire(blastFirst,Items.IRON_INGOT);acquire(blastFirst,Items.COBBLED_DEEPSLATE);
            yes(h,unlocked(blastFirst,BLAST_FURNACE),"blast furnace recipe is unlocked for the reverse order");
            acquire(blastFirst,Items.RAW_GOLD);
            yes(h,unlocked(blastFirst,id("gold_ingot_from_blasting_raw_gold")),"new raw gold after blast furnace is backfilled");
            yes(h,!unlocked(blastFirst,id("gold_ingot_from_blasting_gold_ore")),"unseen gold ore is not backfilled");
        }h.succeed();
    }

    @GameTest(template="empty") public static void routeCountsAreActualRecipeCounts(GameTestHelper h)
    {
        yes(h,recipe_unlock_rules.conditionals().size()>=10,"conditional route definitions are loaded");
        for(var rule:recipe_unlock_rules.pickups())
            com.mojang.logging.LogUtils.getLogger().info("ROUTE_COUNT pickup {} = {}",
                    rule.item(),recipe_unlock_rules.resolve(rule.grant(),h.getLevel()).size());
        for(var rule:recipe_unlock_rules.conditionals())
            com.mojang.logging.LogUtils.getLogger().info("ROUTE_COUNT conditional {} = {}",
                    rule.unlocked(),recipe_unlock_rules.resolve(rule.grant(),h.getLevel()).size());
        h.succeed();
    }

    @GameTest(template="empty") public static void oldSeenHistoryWithoutRecipesIsReconciledAfterRestart(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var p=ProcessingTestPlayers.createWithoutInitialize(h);
            var root=p.getPersistentData();
            var persisted=root.getCompound(Player.PERSISTED_NBT_TAG);
            var data=new CompoundTag();
            var seen=new ListTag();
            seen.add(StringTag.valueOf("minecraft:oak_log"));
            seen.add(StringTag.valueOf("minecraft:cobblestone"));
            data.put("seen_items",seen);
            data.putLong("generation",1L);
            persisted.put(recipe_unlocks.DATA_KEY,data);
            root.put(Player.PERSISTED_NBT_TAG,persisted);
            recipe_unlocks.initialize(p);
            yes(h,unlocked(p,OAK_PLANKS)&&unlocked(p,SPRUCE_PLANKS)&&unlocked(p,STONE_PICKAXE),
                    "an old seen history from the empty-rule build is re-evaluated against new rules after restart");
        }h.succeed();
    }

    @GameTest(template="empty") public static void repeatedDiscoveryDoesNotChangeTheUnlockRevision(GameTestHelper h)
    {
        try(var scope=new ProcessingTestPlayers(h))
        {
            var p=ProcessingTestPlayers.create(h);long before=recipe_unlocks.revision(p);
            acquire(p,Items.OAK_LOG);long after=recipe_unlocks.revision(p);
            acquire(p,Items.OAK_LOG);
            yes(h,after>before&&recipe_unlocks.revision(p)==after,"repeated acquisition does not resync or duplicate grants");
        }h.succeed();
    }
}
